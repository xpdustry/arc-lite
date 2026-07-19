

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
public class UdpConnection implements Disposable{
    InetSocketAddress connectedAddress;
    DatagramChannel datagramChannel;
    int keepAliveMillis = 19000;
    final ByteBuffer readBuffer, writeBuffer;
    private final NetSerializer serialization;
    private SelectionKey selectionKey;
    private final Object writeLock = new Object();
    private long lastCommunicationTime;
    private volatile boolean readPaused, disposed;

    public UdpConnection(NetSerializer serialization, int bufferSize, boolean direct){
        this.serialization = serialization;
        readBuffer = ByteBufferPool.get().obtain(bufferSize, direct);
        writeBuffer = ByteBufferPool.get().obtain(bufferSize, direct);
    }

    public void bind(Selector selector, InetSocketAddress localPort) throws IOException{
        checkDisposed();
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
        checkDisposed();
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
        checkDisposed();
        DatagramChannel datagramChannel = this.datagramChannel;
        if(datagramChannel == null) throw new SocketException("Connection is closed.");
        if(readPaused) return null;
        lastCommunicationTime = Time.millis();

        if(!datagramChannel.isConnected())
            return (InetSocketAddress)datagramChannel.receive(readBuffer); //always null on Android >= 5.0
        datagramChannel.read(readBuffer);
        return connectedAddress;
    }

    public Object readObject(){
        if(disposed) return null;
        if(readPaused) {
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

    /**
     * This method is thread safe.
     */
    public int send(Object object, SocketAddress address) throws IOException{
        checkDisposed();
        DatagramChannel datagramChannel = this.datagramChannel;
        if(datagramChannel == null) throw new SocketException("Connection is closed.");

        synchronized(writeLock){
            try{
                try{
                    serialization.write(writeBuffer, object);
                }catch(Exception ex){
                    throw new ArcNetException("Error serializing object of type: " + object.getClass().getName(), ex);
                }
                writeBuffer.flip();
                int length = writeBuffer.limit();
                datagramChannel.send(writeBuffer, address);

                lastCommunicationTime = Time.millis();

                boolean wasFullWrite = !writeBuffer.hasRemaining();
                return wasFullWrite ? length : -1;
            }finally{
                writeBuffer.clear();
            }
        }
    }

    public void close(){
        connectedAddress = null;
        if(datagramChannel == null) return;
        try{
            datagramChannel.close();
            datagramChannel = null;
            if(selectionKey != null) selectionKey.selector().wakeup();
        }catch(IOException ignored){}
    }

    private void checkDisposed() throws IOException{
        if(disposed) throw new SocketException("Connection is disposed.");
    }

    @Override
    public void dispose(){
        if(disposed) return;
        disposed = true;
        close();
        selectionKey = null;
        ByteBufferPool.free(readBuffer);
        ByteBufferPool.free(writeBuffer);
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
            if(readPaused == paused || selectionKey == null) return;
            readPaused = paused;
            int ops = selectionKey.interestOps();
            selectionKey.interestOps(paused ? (ops & ~SelectionKey.OP_READ) : (ops | SelectionKey.OP_READ));
            selectionKey.selector().wakeup();
        }
    }

    public boolean isReadingPaused() {
        return readPaused;
    }

    public boolean needsKeepAlive(long time){
        return connectedAddress != null && keepAliveMillis > 0
        && time - lastCommunicationTime > keepAliveMillis;
    }
}
