package arc.net;

import java.io.IOException;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.channels.*;

import arc.util.Disposable;
import arc.util.Time;
import arc.util.pooling.ByteBufferPool;

/**
 * @author Nathan Sweet <misc@n4te.com>
 */
@SuppressWarnings("resource")
public class TcpConnection implements Disposable{
    volatile SocketChannel socketChannel;
    int keepAliveMillis = 8000;
    final ByteBuffer readBuffer, writeBuffer;
    int timeoutMillis = 12000;
    float idleThreshold = 0.1f;

    final NetSerializer serialization;
    private volatile SelectionKey selectionKey;
    private volatile long lastWriteTime, lastReadTime;
    private int currentObjectLength;
    private final Object writeLock = new Object();
    private volatile boolean readPaused, disposed;
    private volatile SocketChannel connectingChannel;

    public TcpConnection(NetSerializer serialization, int writeBufferSize, int objectBufferSize, boolean direct){
        this.serialization = serialization;
        writeBuffer = ByteBufferPool.get().obtain(writeBufferSize, direct);
        readBuffer = ByteBufferPool.get().obtain(objectBufferSize, direct);
        readBuffer.flip();
    }

    public SelectionKey accept(Selector selector, SocketChannel socketChannel) throws IOException{
        if(disposed) throw new SocketException("Connection is disposed.");
        writeBuffer.clear();
        readBuffer.clear();
        readBuffer.flip();
        currentObjectLength = 0;
        readPaused = false;

        try{
            this.socketChannel = socketChannel;
            socketChannel.configureBlocking(false);
            Socket socket = socketChannel.socket();
            socket.setTcpNoDelay(true);

            selectionKey = socketChannel.register(selector, SelectionKey.OP_READ);
            lastReadTime = lastWriteTime = Time.millis();

            return selectionKey;
        }catch(IOException ex){
            close();
            throw ex;
        }
    }

    public void connect(Selector selector, SocketAddress remoteAddress, int timeout) throws IOException{
        if(disposed) throw new SocketException("Connection is disposed.");
        close();
        writeBuffer.clear();
        readBuffer.clear();
        readBuffer.flip();
        currentObjectLength = 0;
        readPaused = false;

        SocketChannel socketChannel = null;
        try{
            socketChannel = selector.provider().openSocketChannel();
            connectingChannel = socketChannel;
            Socket socket = socketChannel.socket();
            socket.setTcpNoDelay(true);
            // socket.setTrafficClass(IPTOS_LOWDELAY);
            socket.connect(remoteAddress, timeout); // Connect using blocking mode for simplicity.
            socketChannel.configureBlocking(false);
            this.socketChannel = socketChannel;
            connectingChannel = null;

            selectionKey = socketChannel.register(selector, SelectionKey.OP_READ);
            selectionKey.attach(this);

            lastReadTime = lastWriteTime = Time.millis();
        }catch(IOException | RuntimeException ex){
            connectingChannel = null;
            if(socketChannel != null){
                try{
                    socketChannel.close();
                }catch(IOException ignored){}
            }
            close();
            if(ex instanceof RuntimeException) throw (RuntimeException)ex;
            throw new IOException("Unable to connect to: " + remoteAddress, ex);
        }
    }

    /**
     * Read socket to the {@link #readBuffer}. Then, use {@link #readObject()} to decode the packets.
     * @return whether bytes have been read.
     */
    public boolean readSocket() throws IOException{
        SocketChannel socketChannel = this.socketChannel;
        if(socketChannel == null || readPaused) return false;

        readBuffer.compact();
        int bytesRead;
        try{
            bytesRead = socketChannel.read(readBuffer);
        }catch(ClosedChannelException ex){
            return false; // Closed by another thread while reading
        }finally{
            readBuffer.flip();
        }
        if(bytesRead == -1) throw new SocketException("Connection is closed.");
        if(bytesRead == 0) return false;
        lastReadTime = Time.millis();
        return true;
    }

    /** @return {@code null} if no complete object is available, or if the connection is closed. */
    public Object readObject(){
        if(disposed || socketChannel == null) return null;

        if(currentObjectLength == 0){
            int lengthLength = serialization.getLengthLength();
            if(readBuffer.remaining() < lengthLength) return null;
            currentObjectLength = serialization.readLength(readBuffer);

            if(currentObjectLength <= 0)
                throw new ArcNetException("Invalid object length: " + currentObjectLength);
            if(currentObjectLength > readBuffer.capacity())
                throw new ArcNetException("Unable to read object larger than read buffer: " + currentObjectLength);
        }

        int length = currentObjectLength;
        if(readBuffer.remaining() < length) return null;
        currentObjectLength = 0;

        int startPosition = readBuffer.position();
        int oldLimit = readBuffer.limit();
        readBuffer.limit(startPosition + length);
        Object object;
        try{
            object = serialization.read(readBuffer);
        }catch(Exception ex){
            throw new ArcNetException("Error during deserialization.", ex);
        }finally{
            readBuffer.limit(oldLimit);
        }

        if(readBuffer.position() - startPosition != length)
            throw new ArcNetException("Incorrect number of bytes ("
                                    + (startPosition + length - readBuffer.position())
                                    + " remaining) used to deserialize object: " + object);

        return object;
    }

    public void writeOperation() throws IOException{
        synchronized(writeLock){
            if(socketChannel == null) return;
            try{
                if(writeToSocket()){
                    // Write successful, clear OP_WRITE.
                    setInterestOps(readPaused ? 0 : SelectionKey.OP_READ);
                }
            }catch(IOException ex){
                // Closed by another thread in the meantime
                if(socketChannel == null) return;
                throw ex;
            }
            lastWriteTime = Time.millis();
        }
    }

    /** Must be called while holding {@link #writeLock}. */
    private boolean writeToSocket() throws IOException{
        SocketChannel socketChannel = checkConnected();

        ByteBuffer buffer = writeBuffer;
        buffer.flip();
        try{
            while(buffer.hasRemaining()){
                if(socketChannel.write(buffer) == 0) break;
            }
        }finally{
            buffer.compact();
        }

        return buffer.position() == 0;
    }

    /** This method is thread safe. */
    public int send(Object object) throws IOException{
        synchronized(writeLock){
            checkConnected();

            int start = writeBuffer.position();
            write(serialization, writeBuffer, object);
            int length = writeBuffer.position() - start;

            flushSend(start);
            return length;
        }
    }

    /**
     * Writes the object prefixed by its length, from the position of the buffer.
     * @throws ArcNetException if the object can't be serialized.
     */
    protected static void write(NetSerializer serialization, ByteBuffer buffer, Object object){
        int start = buffer.position(), lengthLength = serialization.getLengthLength();
        try{
            // Leave room for length.
            buffer.position(start + lengthLength);
            serialization.write(buffer, object);
        }catch(Throwable ex){
            throw new ArcNetException("Error serializing object of type: " + object.getClass().getName(), ex);
        }
        int end = buffer.position();
        buffer.position(start);
        serialization.writeLength(buffer, end - lengthLength - start);
        buffer.position(end);
    }

    /**
     * Writes a pre-serialized, length-prefixed buffer directly into this connection's write buffer.
     * Note that this cannot use a raw buffer like UDP, it must contain length!
     * <p>
     * The buffer is rewound before being written, and its position is modified,
     * so the same buffer must not be sent by multiple threads at the same time.
     */
    public int sendBuffer(ByteBuffer buffer) throws IOException{
        synchronized(writeLock){
            checkConnected();

            buffer.rewind();
            int length = buffer.remaining();
            int start = writeBuffer.position();
            if(length > writeBuffer.remaining()){
                throw new ArcNetException("Bulk buffer too large for write buffer: " + length + " > " + writeBuffer.remaining());
            }
            writeBuffer.put(buffer);
            int end = writeBuffer.position();

            flushSend(start);
            return end - start;
        }
    }

    /** Must be called while holding {@link #writeLock}. */
    private void flushSend(int start) throws IOException{
        // Write to socket if no data was queued.
        if(start == 0 && !writeToSocket())
            setInterestOps((readPaused ? 0 : SelectionKey.OP_READ) | SelectionKey.OP_WRITE);
        SelectionKey key = selectionKey;
        if(key != null) key.selector().wakeup();

        lastWriteTime = Time.millis();
    }

    private void setInterestOps(int ops) throws SocketException{
        SelectionKey key = selectionKey;
        if(key == null) throw new SocketException("Connection is closed.");
        try{
            key.interestOps(ops);
        }catch(CancelledKeyException ex){
            throw new SocketException("Connection is closed.");
        }
    }

    public void close(){
        readPaused = false;
        // Interrupt #connect()
        SocketChannel connecting = connectingChannel;
        if(connecting != null){
            connectingChannel = null;
            try{
                connecting.close();
            }catch(IOException ignored){}
        }

        SocketChannel socketChannel = this.socketChannel;
        if(socketChannel == null) return;
        this.socketChannel = null;
        try{
            socketChannel.close();
        }catch(IOException ignored){}
        SelectionKey key = selectionKey;
        if(key != null) key.selector().wakeup();
    }

    @Override
    public void dispose(){
        synchronized(writeLock){
            if(disposed) return;
            disposed = true;
            close();
            selectionKey = null;
            ByteBufferPool.free(readBuffer);
            ByteBufferPool.free(writeBuffer);
        }
    }

    @Override
    public boolean isDisposed(){
        return disposed;
    }

    /**
     * Pause/resume reading. Does nothing if not connected.
     * Objects already received are still delivered.
     * Be aware that pausing for too long can lead to a timeout.
     */
    public void pauseReading(boolean paused){
        if(socketChannel == null) return;
        synchronized(writeLock){
            SelectionKey key = selectionKey;
            if(readPaused == paused || key == null) return;
            readPaused = paused;
            try{
                int ops = key.interestOps();
                key.interestOps(paused ? (ops & ~SelectionKey.OP_READ) : (ops | SelectionKey.OP_READ));
                key.selector().wakeup();
            }catch(CancelledKeyException ignored){} // Connection closed
        }
    }

    public boolean isReadingPaused(){
        return readPaused;
    }

    public boolean needsKeepAlive(long time){
        return socketChannel != null && keepAliveMillis > 0 && time - lastWriteTime > keepAliveMillis;
    }

    public boolean isTimedOut(long time){
        return socketChannel != null && timeoutMillis > 0 && time - lastReadTime > timeoutMillis;
    }

    private SocketChannel checkConnected() throws IOException{
        SocketChannel channel = socketChannel;
        if(channel == null) throw new SocketException("Connection is closed.");
        return channel;
    }
}
