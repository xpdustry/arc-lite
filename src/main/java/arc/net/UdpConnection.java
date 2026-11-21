package arc.net;

import java.io.IOException;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.channels.*;

import arc.util.Disposable;
import arc.util.Time;
import arc.util.io.Streams;
import arc.util.pooling.ByteBufferPool;

/**
 * @author Nathan Sweet <misc@n4te.com>
 */
@SuppressWarnings("resource")
public class UdpConnection implements Disposable{
    volatile InetSocketAddress connectedAddress;
    volatile DatagramChannel datagramChannel;
    int keepAliveMillis = 19000;
    final ByteBuffer readBuffer, writeBuffer;
    private final NetSerializer serialization;
    private volatile SelectionKey selectionKey;
    private final Object writeLock = new Object();
    private volatile long lastCommunicationTime;
    private volatile boolean readPaused, disposed;

    public UdpConnection(NetSerializer serialization, int bufferSize){
        this(serialization, bufferSize, false);
    }

    public UdpConnection(NetSerializer serialization, int bufferSize, boolean direct){
        this.serialization = serialization;
        readBuffer = ByteBufferPool.get().obtain(bufferSize, direct);
        writeBuffer = ByteBufferPool.get().obtain(bufferSize, direct);
    }

    public void bind(Selector selector, InetSocketAddress localPort) throws IOException{
        if(disposed) throw new SocketException("Connection is disposed.");
        close();
        readBuffer.clear();
        writeBuffer.clear();
        readPaused = false;

        try{
            datagramChannel = selector.provider().openDatagramChannel();
            datagramChannel.socket().bind(localPort);
            datagramChannel.configureBlocking(false);

            selectionKey = datagramChannel.register(selector, SelectionKey.OP_READ);
            lastCommunicationTime = Time.millis();
        }catch(IOException ex){
            close();
            throw ex;
        }
    }

    public void connect(Selector selector, InetSocketAddress remoteAddress) throws IOException{
        if(disposed) throw new SocketException("Connection is disposed.");
        close();
        readBuffer.clear();
        writeBuffer.clear();
        readPaused = false;

        try{
            datagramChannel = selector.provider().openDatagramChannel();
            datagramChannel.socket().bind(null);
            datagramChannel.socket().connect(remoteAddress);
            datagramChannel.configureBlocking(false);

            selectionKey = datagramChannel.register(selector, SelectionKey.OP_READ);
            lastCommunicationTime = Time.millis();
            connectedAddress = remoteAddress;
        }catch(IOException ex){
            close();
            throw new IOException("Unable to connect to: " + remoteAddress, ex);
        }
    }

    public InetSocketAddress readFromAddress() throws IOException{
        DatagramChannel datagramChannel = this.datagramChannel;
        if(datagramChannel == null || readPaused) return null;
        lastCommunicationTime = Time.millis();

        try{
            if(!datagramChannel.isConnected())
                return (InetSocketAddress)datagramChannel.receive(readBuffer); //always null on Android >= 5.0
            return datagramChannel.read(readBuffer) > 0 ? connectedAddress : null;
        }catch(ClosedChannelException ex){
            return null; // Closed by another thread while reading
        }
    }

    public Object readObject(){
        if(disposed) return null;
        if(readPaused){
            readBuffer.clear();
            return null;
        }
        readBuffer.flip();
        try{
            try{
                Object object = serialization.read(readBuffer);
                if(readBuffer.hasRemaining())
                    throw new ArcNetException("Incorrect number of bytes (" + readBuffer.remaining()
                                            + " remaining) used to deserialize object: " + object);
                return object;
            }catch(Exception ex){
                throw new ArcNetException("Error during UDP deserialization.", ex);
            }
        }finally{
            readBuffer.clear();
        }
    }

    /** Serializes the specified object. This method is thread safe. */
    public int send(Object object, SocketAddress address) throws IOException{
        synchronized(writeLock){
            DatagramChannel datagramChannel = this.datagramChannel;
            if(datagramChannel == null) throw new SocketException("Connection is closed.");

            try{
                write(serialization, writeBuffer, object);
                writeBuffer.flip();
                int length = writeBuffer.limit();
                datagramChannel.send(writeBuffer, address);

                lastCommunicationTime = Time.millis();

                return !writeBuffer.hasRemaining() ? length : -1;
            }finally{
                writeBuffer.clear();
            }
        }
    }

    /**
     * Writes the object from the position of the buffer.
     * @throws ArcNetException if the object can't be serialized.
     */
    protected static void write(NetSerializer serialization, ByteBuffer buffer, Object object){
        try{
            serialization.write(buffer, object);
        }catch(Throwable ex){
            throw new ArcNetException("Error serializing object of type: " + object.getClass().getName(), ex);
        }
    }

    /**
     * Directly sends an entire buffer to this address. No serialization is performed.
     * <p>
     * The buffer is rewound before being sent, and its position is modified,
     * so the same buffer must not be sent by multiple threads at the same time.
     */
    public int sendBuffer(ByteBuffer buffer, SocketAddress address) throws IOException{
        DatagramChannel datagramChannel = this.datagramChannel;
        if(datagramChannel == null) throw new SocketException("Connection is closed.");

        //note: writeBuffer is not used and thus writeLock is not needed
        buffer.rewind();
        int length = buffer.remaining();
        datagramChannel.send(buffer, address);
        lastCommunicationTime = Time.millis();
        return !buffer.hasRemaining() ? length : -1;
    }

    public void close(){
        connectedAddress = null;
        DatagramChannel datagramChannel = this.datagramChannel;
        if(datagramChannel == null) return;
        this.datagramChannel = null;
        Streams.close(datagramChannel);
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
     * This will silently drop new datagrams when buffer is full.
     */
    public void pauseReading(boolean paused){
        if(datagramChannel == null) return;
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
        return connectedAddress != null && keepAliveMillis > 0
        && time - lastCommunicationTime > keepAliveMillis;
    }
}
