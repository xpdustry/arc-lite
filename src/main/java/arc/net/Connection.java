

package arc.net;

import arc.net.FrameworkMessage.Ping;

import java.io.IOException;
import java.net.Socket;
import java.net.*;
import java.nio.channels.SocketChannel;

/**
 * Represents a TCP and optionally a UDP connection between a {@link Client} and
 * a {@link Server}. If either underlying connection is closed or errors, both
 * connections are closed.
 * @author Nathan Sweet <misc@n4te.com>
 */
public class Connection{
    int id = -1;
    protected String name;
    EndPoint endPoint;
    TcpConnection tcp;
    UdpConnection udp;
    InetSocketAddress udpRemoteAddress;
    protected final DispatchListener listeners = new DispatchListener(true);
    protected int lastPingID;
    protected long lastPingSendTime;
    protected int returnTripTime;
    volatile boolean isConnected;
    volatile ArcNetException lastProtocolError;
    private Object arbitraryData;

    protected Connection(){
    }

    void initialize(NetSerializer serialization, int writeBufferSize, int objectBufferSize){
        tcp = new TcpConnection(serialization, writeBufferSize,
        objectBufferSize);
    }

    /**
     * Returns the server assigned ID. Will return -1 if this connection has
     * never been connected or the last assigned ID if this connection has been
     * disconnected.
     */
    public int getID(){
        return id;
    }

    /**
     * Returns true if this connection is connected to the remote end. Note that
     * a connection can become disconnected at any time.
     */
    public boolean isConnected(){
        return isConnected;
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
        boolean wasConnected = isConnected;
        isConnected = false;
        tcp.close();
        if(udp != null && udp.connectedAddress != null)
            udp.close();
        if(wasConnected){
            notifyDisconnected(reason);
        }
        setConnected(false);
    }

    /**
     * Requests the connection to communicate with the remote computer to
     * determine a new value for the {@link #getReturnTripTime() return trip
     * time}. When the connection receives a {@link FrameworkMessage.Ping}
     * object with {@link Ping#isReply isReply} set to true, the new return trip
     * time is available.
     */
    public void updateReturnTripTime(){
        Ping ping = new Ping();
        ping.id = lastPingID++;
        lastPingSendTime = System.currentTimeMillis();
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
     * within the specified milliseconds. Periodically sending a keep alive
     * ensures that an abnormal close is detected in a reasonable amount of time
     * (see {@link #setTimeout(int)} ). Also, some network hardware will close a
     * TCP connection that ceases to transmit for a period of time (typically 1+
     * minutes). Set to zero to disable. Defaults to 8000.
     */
    public void setKeepAliveTCP(int keepAliveMillis){
        tcp.keepAliveMillis = keepAliveMillis;
    }

    /**
     * If the specified amount of time passes without receiving an object over
     * TCP, the connection is considered closed. When a TCP socket is closed
     * normally, the remote end is notified immediately and this timeout is not
     * needed. However, if a socket is closed abnormally (eg, power loss),
     * ArcNet uses this timeout to detect the problem. The timeout should be
     * set higher than the {@link #setKeepAliveTCP(int) TCP keep alive} for the
     * remote end of the connection. The keep alive ensures that the remote end
     * of the connection will be constantly sending objects, and setting the
     * timeout higher than the keep alive allows for network latency. Set to
     * zero to disable. Defaults to 12000.
     */
    public void setTimeout(int timeoutMillis){
        tcp.timeoutMillis = timeoutMillis;
    }

    /**
     * Adds a listener to the connection. If the listener already exists, it is
     * not added again.
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
                    returnTripTime = (int)(System.currentTimeMillis()
                    - lastPingSendTime);
                }
            }else{
                ping.isReply = true;
                sendTCP(ping);
            }
        }

        listeners.received(this, object);
    }

    /**
     * Returns the local {@link Client} or {@link Server} to which this
     * connection belongs.
     */
    public EndPoint getEndPoint(){
        return endPoint;
    }

    /**
     * Returns the IP address and port of the remote end of the TCP connection,
     * or null if this connection is not connected.
     */
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
        return udp.connectedAddress != null ? udp.connectedAddress : udpRemoteAddress;
    }

    /**
     * Sets the friendly name of this connection. This is returned by
     * {@link #toString()} and is useful for providing application specific
     * identifying information in the logging. May be null for the default name
     * of "Connection X", where X is the connection ID.
     */
    public void setName(String name){
        this.name = name;
    }

    /**
     * Returns the number of bytes that are waiting to be written to the TCP
     * socket, if any.
     */
    public int getTcpWriteBufferSize(){
        return tcp.writeBuffer.position();
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
        if(name != null)
            return name;
        return "Connection " + id;
    }

    void setConnected(boolean isConnected){
        this.isConnected = isConnected;
        if(isConnected && name == null)
            name = "Connection " + id;
    }

    public Object getArbitraryData(){
        return arbitraryData;
    }

    public void setArbitraryData(Object arbitraryData){
        this.arbitraryData = arbitraryData;
    }
}
