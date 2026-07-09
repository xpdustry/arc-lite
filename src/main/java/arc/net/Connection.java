

package arc.net;

import arc.net.FrameworkMessage.Ping;
import arc.util.Disposable;
import arc.util.Time;

import java.io.IOException;
import java.net.Socket;
import java.net.*;
import java.nio.channels.SocketChannel;

/**
 * Represents a TCP and optionally a UDP connection between a {@link Client} and a {@link Server}.
 * If either underlying connection is closed or errors, both connections are closed.
 * @author Nathan Sweet <misc@n4te.com>
 */
public class Connection implements Disposable{
    int id;
    protected String name;
    EndPoint endPoint;
    TcpConnection tcp;
    UdpConnection udp;
    InetSocketAddress udpRemoteAddress;
    protected final DispatchListener listeners = new DispatchListener(true);
    protected int lastPingID;
    protected long lastPingSendTime;
    protected int returnTripTime;
    volatile boolean isConnected, disposed, udpPaused;
    volatile ArcNetException lastProtocolError;
    private Object arbitraryData;
    boolean tcpOnly;

    protected Connection(){
    }

    void initialize(NetSerializer serialization, int writeBufferSize, int objectBufferSize, boolean direct){
        tcp = new TcpConnection(serialization, writeBufferSize, objectBufferSize, direct);
    }

    /**
     * Returns the server assigned ID.
     * Will return {@code 0} if this connection has never been connected
     * or the last assigned ID if this connection has been disconnected.
     */
    public int getID(){
        return id;
    }

    /**
     * Returns true if this connection is connected to the remote end.
     * Note that a connection can become disconnected at any time.
     */
    public boolean isConnected(){
        return isConnected;
    }

    public boolean isUDPConnected(){
        return !tcpOnly && (udpRemoteAddress != null || isClientUDP());
    }

    /**
     * @return whether the UDP connection is from a client or a server.
     * As on a server, the UDP socket is shared among all connections.
     */
    public boolean isClientUDP() {
        return udp != null && udp.connectedAddress != null;
    }

    /**
     * Returns the last protocol error that occured on the connection.
     * @return The last protocol error or null if none error occured.
     */
    public ArcNetException getLastProtocolError(){
        return lastProtocolError;
    }

    /**
     * Sends the object over the network using TCP.
     * @return The number of bytes sent.
     */
    public int sendTCP(Object object){
        if(object == null) throw new IllegalArgumentException("object cannot be null.");
        checkDisposed();

        try{
            return tcp.send(object);
        }catch(IOException | ArcNetException ex){
            close(DcReason.error);
            ArcNet.handleError(ex);
            return 0;
        }
    }

    /**
     * Sends the object over the network using UDP.
     * @return The number of bytes sent.
     * @throws IllegalStateException if this connection was not opened with both TCP and UDP.
     */
    public int sendUDP(Object object){
        if(object == null)
            throw new IllegalArgumentException("object cannot be null.");
        checkDisposed();
        SocketAddress address = udpRemoteAddress;
        if(address == null && udp != null)
            address = udp.connectedAddress;
        if(address == null && isConnected)
            throw new IllegalStateException("Connection is not connected via UDP.");

        try{
            if(address == null) throw new SocketException("Connection is closed.");
            return udp.send(object, address);
        }catch(IOException | ArcNetException ex){
            close(DcReason.error);
            ArcNet.handleError(ex);
            return 0;
        }
    }

    public void close(DcReason reason){
        udpPaused = false;
        boolean wasConnected = isConnected;
        isConnected = false;
        tcp.close();
        if(isClientUDP()) udp.close();
        if(wasConnected) notifyDisconnected(reason);
        setConnected(false);
    }

    protected void checkDisposed(){
        if(disposed) throw new IllegalStateException("Connection is disposed.");
    }

    /**
     * Dispose any resources of this connection. Therefore, it cannot be used anymore.
     */
    @Override
    public void dispose(){
        disposed = true;
        close(DcReason.closed);
        tcp.dispose();
        if(isClientUDP()) udp.dispose();
    }

    @Override
    public boolean isDisposed(){
        return disposed;
    }

    /**
     * Requests the connection to communicate with the remote computer to determine
     * a new value for the {@link #getReturnTripTime() return trip time}.
     * When the connection receives a {@link FrameworkMessage.Ping} object with
     * {@link Ping#isReply isReply} set to true, the new return trip time is available.
     */
    public void updateReturnTripTime(){
        checkDisposed();
        Ping ping = new Ping();
        ping.id = lastPingID++;
        lastPingSendTime = Time.millis();
        sendTCP(ping);
    }

    /**
     * Returns the last calculated TCP return trip time, or -1 if
     * {@link #updateReturnTripTime()} has never been called or the
     * {@link FrameworkMessage.Ping} response has not yet been received.
     */
    public int getReturnTripTime(){
        return returnTripTime;
    }

    /**
     * An empty object will be sent if the TCP connection has not sent an object
     * within the specified milliseconds.
     * Periodically sending a keep alive ensures that an abnormal close is detected
     * in a reasonable amount of time (see {@link #setTimeout(int)} ).
     * Also, some network hardware will close a TCP connection that ceases to
     * transmit for a period of time (typically 1+ minutes).
     * Set to zero to disable. Defaults to {@code 8000}.
     */
    public void setKeepAliveTCP(int keepAliveMillis){
        tcp.keepAliveMillis = keepAliveMillis;
    }

    /**
     * If the specified amount of time passes without receiving an object over TCP,
     * the connection is considered closed.
     * When a TCP socket is closed normally, the remote end is notified immediately
     * and this timeout is not needed.
     * However, if a socket is closed abnormally (eg, power loss), ArcNet uses this
     * timeout to detect the problem.
     * The timeout should be set higher than the {@link #setKeepAliveTCP(int) TCP
     * keep alive} for the  remote end of the connection.
     * The keep alive ensures that the remote end of the connection will be constantly
     * sending objects, and setting the timeout higher than the keep alive allows for
     * network latency.
     * Set to zero to disable. Defaults to 12000.
     */
    public void setTimeout(int timeoutMillis){
        tcp.timeoutMillis = timeoutMillis;
    }

    /**
     * Adds a listener to the connection.
     * If the listener already exists, it is not added again.
     * @param listener The listener to add.
     */
    public void addListener(NetListener listener){
        listeners.addListener(listener);
    }

    public void removeListener(NetListener listener){
        listeners.removeListener(listener);
    }

    protected void notifyConnected(){
        listeners.connected(this);
    }

    protected void notifyDisconnected(DcReason reason){
        listeners.disconnected(this, reason);
    }

    protected void notifyIdle(){
        listeners.idle(this);
    }

    protected void notifyReceived(Object object){
        if(object instanceof Ping){
            Ping ping = (Ping)object;
            if(ping.isReply){
                if(ping.id == lastPingID - 1){
                    returnTripTime = (int)Time.sinceMillis(lastPingSendTime);
                }
            }else{
                ping.isReply = true;
                sendTCP(ping);
                ping.isReply = false; // restore state so listeners can know when it's a server ping
            }
        }

        listeners.received(this, object);
    }

    /**
     * Returns the local {@link Client} or {@link Server} to which this connection belongs.
     */
    public EndPoint getEndPoint(){
        return endPoint;
    }

    /**
     * Returns the IP address and port of the remote end of the TCP connection,
     * or null if this connection is not connected.
     */
    @SuppressWarnings("resource")
    public InetSocketAddress getRemoteAddressTCP(){
        SocketChannel socketChannel = tcp.socketChannel;
        if(socketChannel != null){
            Socket socket = tcp.socketChannel.socket();
            if(socket != null){
                return (InetSocketAddress)socket.getRemoteSocketAddress();
            }
        }
        return null;
    }

    /**
     * Returns the IP address and port of the remote end of the UDP connection,
     * or null if this connection is not connected.
     */
    public InetSocketAddress getRemoteAddressUDP(){
        return isClientUDP() ? udp.connectedAddress : udpRemoteAddress;
    }

    /**
     * Sets the friendly name of this connection. This is returned by
     * {@link #toString()} and is useful for providing application specific
     * identifying information in the logging.
     * May be null for the default name of "Connection X", where X is the connection ID.
     */
    public void setName(String name){
        this.name = name;
    }

    /**
     * Returns the number of bytes that are waiting to be written to the TCP socket, if any.
     */
    public int getTcpWriteBufferSize(){
        return tcp.writeBuffer.position();
    }

    /**
     * Returns the number of bytes remaining in the TCP buffer.
     * Can be used to determine if the write buffer is about to overflow.
     */
    public int getTcpWriteBufferRemaining(){
        return tcp.writeBuffer.remaining();
    }

    /**
     * @see #setIdleThreshold(float)
     */
    public boolean isIdle(){
        return tcp.writeBuffer.position() / (float)tcp.writeBuffer.capacity() < tcp.idleThreshold;
    }

    /**
     * If the percent of the TCP write buffer that is filled is less than the
     * specified threshold, {@link NetListener#idle(Connection)} will be called for
     * each network thread update. Default is 0.1.
     */
    public void setIdleThreshold(float idleThreshold){
        tcp.idleThreshold = idleThreshold;
    }

    @Override
    public String toString(){
        return name != null ? name : "Connection " + getID();
    }

    protected void setConnected(boolean isConnected){
        this.isConnected = isConnected;
        if(isConnected && name == null) name = "Connection " + getID();
    }

    public Object getArbitraryData(){
        return arbitraryData;
    }

    public void setArbitraryData(Object arbitraryData){
        this.arbitraryData = arbitraryData;
    }

    /**
     * Pause/resume TCP reading. Does nothing if not connected.
     * Be aware that pausing for too long can lead to a timeout.
     */
    public void pauseTCPReading(boolean pause){
        tcp.pauseReading(pause);
    }

    public boolean isTCPPaused(){
        return tcp.isReadingPaused();
    }

    /**
     * Pause/resume UDP reading. Does nothing if not connected.
     * New datagrams will be silently dropped if this connection comes from a server,
     * otherwise they will only be if buffer is full.
     */
    public void pauseUDPReading(boolean pause){
        if(isClientUDP()) udp.pauseReading(pause);
        udpPaused = true;
    }

    public boolean isUDPPaused(){
        return udpPaused || udp.isReadingPaused();
    }
}
