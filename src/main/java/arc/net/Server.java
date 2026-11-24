package arc.net;

import arc.func.Boolf;
import arc.func.Cons;
import arc.math.*;
import arc.net.FrameworkMessage.*;
import arc.struct.*;
import arc.util.*;
import arc.util.io.Streams;
import arc.util.pooling.ByteBufferPool;

import java.io.*;
import java.net.*;
import java.nio.*;
import java.nio.channels.*;
import java.util.*;

/**
 * Manages TCP and optionally UDP connections from many {@linkplain Client Clients}.
 * <p>
 * A connection that didn't complete its registration within
 * its {@linkplain Connection#setTimeout(int) timeout} is closed.
 * @author Nathan Sweet <misc@n4te.com>
 */
public class Server implements EndPoint{
    protected final NetSerializer serializer;
    protected final int writeBufferSize, objectBufferSize;
    protected final boolean directBuffers;

    private final Selector selector;
    private int emptySelects;
    private long emptySelectsStart;
    private volatile ServerSocketChannel serverChannel;
    private volatile UdpConnection udp;

    /** Registered connections. Copy-on-write. */
    private volatile Connection[] connections = {};
    /** Registered connections by id. Copy-on-write. */
    private volatile IntMap<Connection> connectionsMap = new IntMap<>();
    private final IntMap<Connection> pendingConnections = new IntMap<>();
    private final ObjectMap<InetSocketAddress, Connection> udpAddressToConnection = new ObjectMap<>();
    /** Whether the selected keys have been modified while the update thread iterates them. */
    private boolean keysChanged;
    private final Rand rand = new Rand();

    private volatile boolean shutdown = true, starting;
    /**
     * Held by the update thread while it processes the selected keys and the connections,
     * and by the other threads to bind or close the server.
     */
    private final Object updateLock = new Object();
    private volatile Thread updateThread;

    protected int readsPerSelect = 16; // Same as Netty
    protected boolean allowTcpOnly;
    protected int multicastPort = 21010;
    protected InetAddress multicastGroup;
    protected DiscoveryReceiver discoveryReceiver;
    protected ServerDiscoveryHandler discoveryHandler;
    protected ServerConnectFilter connectFilter;
    protected final DispatchListener dispatchListener = new DispatchListener();

    public Server(int writeBufferSize, int objectBufferSize, NetSerializer serializer){
        this(writeBufferSize, objectBufferSize, false, serializer);
    }

    /**
     * @param writeBufferSize One buffer of this size is allocated for each connected client.
     * Objects are serialized to the write buffer where the bytes are queued until they can
     * be written to the TCP socket.
     * <p>
     * Normally the socket is writable and the bytes are written immediately.
     * If the socket cannot be written to and enough serialized objects are queued to overflow
     * the buffer, then the connection will be closed.
     * <p>
     * The write buffer should be sized at least as large as the largest object that will be sent,
     * plus some head room to allow for some serialized objects to be queued in case the buffer is
     * temporarily not writable.
     * The amount of head room needed is dependent upon the size of objects being sent and how often
     * they are sent.
     *
     * @param objectBufferSize One (using only TCP) or three (using both TCP and UDP) buffers
     * of this size are allocated.
     * These buffers are used to hold the bytes for a single object graph until it can be sent over
     * the network or deserialized.
     * <p>
     * The object buffers should be sized at least as large as the largest object that will be sent
     * or received.
     */
    public Server(int writeBufferSize, int objectBufferSize, boolean directBuffers, NetSerializer serializer){
        this.writeBufferSize = writeBufferSize;
        this.objectBufferSize = objectBufferSize;
        this.directBuffers = directBuffers;
        this.serializer = serializer;
        this.discoveryHandler = (address, handler) -> handler.respond(ByteBufferPool.getHeap(0));

        Selector s;
        try{
            try{
                s = NioUtils.newOptimizedSelector();
            }catch(RuntimeException ignored){
                s = NioUtils.newSelector();
            }
        }catch(IOException ex){
            throw new RuntimeException("Error opening selector.", ex);
        }
        selector = s;
    }

    /**
     * Sets the maximum number of reads per select, to avoid a read monopolization.
     * {@code 0} to disable, {@code 1} for a single read per iteration.
     */
    public void readsPerSelect(int maxReads){
        readsPerSelect = maxReads < 1 ? Integer.MAX_VALUE : maxReads;
    }

    /**
     * Sets whether a client can register as TCP only. Otherwise, its connection is closed.
     */
    public void allowTcpOnlyConnections(boolean allow){
        allowTcpOnly = allow;
    }

    public void setMulticast(String group, int multicastPort){
        this.multicastPort = multicastPort;
        try{
            multicastGroup = InetAddress.getByName(group);
        }catch(IOException e){
            ArcNet.handleError(e);
        }
    }

    public void setDiscoveryHandler(ServerDiscoveryHandler newDiscoveryHandler){
        discoveryHandler = newDiscoveryHandler;
    }

    public void setConnectFilter(ServerConnectFilter connectFilter){
        this.connectFilter = connectFilter;
    }

    public ServerConnectFilter getConnectFilter(){
        return connectFilter;
    }

    /**
     * Opens a TCP only server.
     * @throws IOException if the server could not be opened.
     */
    public void bind(int tcpPort) throws IOException{
        bind(new InetSocketAddress(tcpPort), null);
    }

    /**
     * Opens a TCP and UDP server. All clients must also have a TCP and an UDP port.
     * @throws IOException if the server could not be opened.
     */
    public void bind(int tcpPort, int udpPort) throws IOException{
        bind(new InetSocketAddress(tcpPort), new InetSocketAddress(udpPort));
    }

    /**
     * @param udpPort May be null.
     */
    public void bind(InetSocketAddress tcpPort, InetSocketAddress udpPort) throws IOException{
        //TODO: safe to lock here? Some JDKs seems to block registration if a select occur while binding.
        synchronized(updateLock){
            close();
            selector.wakeup();
            try{
                serverChannel = selector.provider().openServerSocketChannel();
                serverChannel.socket().bind(tcpPort);
                serverChannel.configureBlocking(false);
                serverChannel.register(selector, SelectionKey.OP_ACCEPT);

                if(udpPort != null){
                    UdpConnection udp = new UdpConnection(serializer, objectBufferSize, directBuffers);
                    this.udp = udp;
                    udp.bind(selector, udpPort);
                }

                if(multicastGroup != null && (udpPort == null || multicastPort != udpPort.getPort())){
                    discoveryReceiver = new DiscoveryReceiver();
                    discoveryReceiver.start();
                }
            }catch(Exception ex){
                close();
                throw ex;
            }
        }
    }

    /**
     * Accepts any new connections and reads or writes any pending data for the current connections.
     * Must always be called by the same dedicated thread.
     * @param timeout Wait for up to the specified milliseconds for a connection to be ready to process.
     * May be zero to return immediately if there are no connections to process.
     */
    @Override
    public void update(int timeout) throws IOException{
        updateThread = Thread.currentThread();
        Set<SelectionKey> keys = select(timeout);

        synchronized(updateLock){
            if(keys != null){
                keysChanged = false;
                for(Iterator<SelectionKey> iter = keys.iterator(); !keysChanged && iter.hasNext();){
                    SelectionKey selectionKey = iter.next();
                    iter.remove();

                    try{
                        doSelect(selectionKey);
                    }catch(CancelledKeyException ignored){
                        // Connection closed while processing another key
                    }
                }
            }

            updateConnections();
        }
    }

    Set<SelectionKey> select(int timeout) throws IOException{
        if((timeout > 0 ? selector.select(timeout) : selector.selectNow()) == 0){
            // NIO freaks and returns immediately with 0 sometimes, so try to keep from hogging the CPU.
            long now = Time.nanos();
            if(emptySelects++ == 0) emptySelectsStart = now;
            else if(emptySelects >= 100){
                emptySelects = 0;
                if(timeout > 0 && now - emptySelectsStart < 2_000_000L) Threads.sleepQuietly(1);
            }
        }else emptySelects = 0;
        Set<SelectionKey> keys = selector.selectedKeys();
        return keys.isEmpty() ? null : keys;
    }

    protected void doSelect(SelectionKey selectionKey) throws IOException{
        Connection fromConnection = (Connection)selectionKey.attachment();
        int ops = selectionKey.readyOps();

        if(fromConnection != null){ // Must be a TCP read or write operation.
            if((ops & SelectionKey.OP_READ) == SelectionKey.OP_READ){
                TcpConnection tcp = fromConnection.tcp;
                try{
                    int reads = 0;
                    do{
                        while(doSelectTCP(fromConnection, tcp));
                    }while(reads++ < readsPerSelect && tcp.readSocket());
                }catch(IOException | ArcNetException ex){
                    ArcNet.handleError(new ArcNetException("Error reading TCP from connection: " + fromConnection, ex));
                    fromConnection.close(closeReason(ex.getMessage()));
                }
            }

            if((ops & SelectionKey.OP_WRITE) == SelectionKey.OP_WRITE){
                try{
                    fromConnection.tcp.writeOperation();
                }catch(IOException ex){
                    fromConnection.close(closeReason(ex.getMessage()));
                }
            }
            return;
        }

        if((ops & SelectionKey.OP_ACCEPT) == SelectionKey.OP_ACCEPT){
            try{
                SocketChannel socketChannel = ((ServerSocketChannel)selectionKey.channel()).accept();
                if(socketChannel != null) acceptOperation(socketChannel);
            }catch(IOException ex){
                ArcNet.handleError(ex);
            }
            return;
        }

        // Must be a UDP read operation.
        UdpConnection udp = this.udp;
        if(udp == null) return; // Closed by another thread
        for(int i = 0; i < readsPerSelect; i++){
            InetSocketAddress fromAddress;
            try{
                fromAddress = udp.readFromAddress();
            }catch(IOException ex){
                ArcNet.handleError(ex);
                return;
            }
            if(fromAddress == null) return;
            doSelectUDP(udp, udpAddressToConnection.get(fromAddress), fromAddress);
        }
    }

    protected boolean doSelectTCP(Connection fromConnection, TcpConnection tcp){
        Object object = tcp.readObject();
        if (object == null) return false;
        // Still awaiting its registration, do not accept other packets
        else if(fromConnection.udp != null && !fromConnection.tcpOnly &&
                fromConnection.udpRemoteAddress == null){
            // The client wants TCP only.
            if(object instanceof RegisterTCP){
                if(!allowTcpOnly){
                    fromConnection.close(DcReason.error);
                    return false;
                }
                pendingConnections.remove(fromConnection.getID());
                fromConnection.tcpOnly = true;
                addConnection(fromConnection);
                fromConnection.notifyConnected();
            }
        }else if(!(object instanceof RegisterTCP)) fromConnection.notifyReceived(object);
        return true;
    }

    protected boolean doSelectUDP(UdpConnection udp, Connection fromConnection, InetSocketAddress fromAddress) throws IOException{
        if(fromConnection != null && (!fromConnection.isConnected() || fromConnection.udpPaused)){
            udp.readBuffer.clear();
            return false;
        }

        Object object;
        try{
            object = udp.readObject();
        }catch(ArcNetException ex){
            ArcNet.handleError(new ArcNetException("Error reading UDP from connection: " + fromAddress, ex));
            return false;
        }

        if(object instanceof FrameworkMessage){
            if(object instanceof RegisterUDP){
                // Store the fromAddress on the connection and reply over TCP
                // with a RegisterUDP to indicate success.
                int fromConnectionID = ((RegisterUDP)object).connectionID;
                Connection connection = pendingConnections.get(fromConnectionID);
                if(connection == null) return false;
                //TODO: remove that, this is not safe.
                // It is illegal to register an UDP connection without the same address as the TCP one.
                // The datagram is simply dropped, the connection stays pending until it times out.
                InetSocketAddress toAddress = connection.getRemoteAddressTCP();
                if(toAddress == null || !fromAddress.getAddress().equals(toAddress.getAddress())) return false;
                pendingConnections.remove(fromConnectionID);

                connection.udpRemoteAddress = fromAddress;
                addConnection(connection);
                connection.sendTCP(object);
                if(connection.isConnected()) connection.notifyConnected();
                return true;
            }

            if(object instanceof DiscoverHost){
                try{
                    discoveryHandler.onDiscoverReceived(fromAddress.getAddress(), buff -> {
                        DatagramChannel channel = udp.datagramChannel;
                        if(channel != null) channel.send(buff, fromAddress);
                    });
                }catch(Exception ex){
                    ArcNet.handleError(ex);
                }
                return true;
            }
        }

        if(fromConnection == null) return false;
        fromConnection.notifyReceived(object);
        return true;
    }

    DcReason closeReason(String errMsg){
        return errMsg != null && errMsg.contains("closed") ? DcReason.closed : DcReason.error;
    }

    public void keepAlive(){
        long time = Time.millis();
        for(Connection connection : connections){
            if(connection.tcp.needsKeepAlive(time)) connection.sendTCP(FrameworkMessage.keepAlive);
        }
    }

    @Override
    public void run(){
        if(!starting) shutdown = false;
        starting = false;
        try{
            while(!shutdown){
                try{
                    update(250);
                }catch(IOException ex){
                    ArcNet.handleError(ex);
                    close();
                }
            }
        }catch(ClosedSelectorException e){
            // Disposed by another thread, nothing to do
        }catch(Exception e){
            try{
                close();
            }catch(Exception e2){
                e.addSuppressed(e2);
            }
            throw e;
        }finally{
            shutdown = true;
        }
    }

    @Override
    public void start(){
        Thread thread = updateThread;
        boolean alive = thread != null && thread.isAlive();
        if(alive && (!shutdown || thread == Thread.currentThread())){
            shutdown = false;
            return;
        }else if(alive){
            selector.wakeup();
            Threads.joinQuietly(thread, 5000);
        }

        starting = true;
        shutdown = false;
        updateThread = Threads.thread("Server", this);
    }

    @Override
    public void stop(){
        if(shutdown) return;
        close();
        shutdown = true;
        selector.wakeup();
    }

    @Override
    public boolean isStopped(){
        return shutdown;
    }

    @Override
    public boolean isStarting(){
        return starting;
    }

    private void acceptOperation(SocketChannel socketChannel){
        if(connectFilter != null){
            boolean accepted;
            try{
                accepted = connectFilter.accept((InetSocketAddress)socketChannel.getRemoteAddress());
            }catch(Exception ex){
                ArcNet.handleError(ex);
                accepted = false;
            }
            if(!accepted || serverChannel == null){
                Streams.close(socketChannel);
                return;
            }
        }

        Connection connection = newConnection();
        connection.initialize(serializer, writeBufferSize, objectBufferSize, directBuffers);
        connection.endPoint = this;
        UdpConnection udp = this.udp;
        if(udp != null) connection.udp = udp;
        else connection.tcpOnly = true; // Without UDP, every connections are TCP only

        try{
            SelectionKey selectionKey = connection.tcp.accept(selector, socketChannel);
            selectionKey.attach(connection);

            int id = generateId();
            connection.id = id;
            connection.acceptTime = Time.millis();
            connection.setConnected(true);
            connection.addListener(dispatchListener);

            if(udp == null) addConnection(connection);
            else pendingConnections.put(id, connection);

            RegisterTCP registerConnection = new RegisterTCP();
            registerConnection.connectionID = id;
            connection.sendTCP(registerConnection);

            if(udp == null && connection.isConnected()) connection.notifyConnected();
        }catch(IOException ex){
            connection.close(DcReason.error);
            connection.dispose();
        }
    }

    protected int generateId(){
        int id;
        do{
            id = rand.nextInt();
        }while(id == 0 || isPending(id) || getConnection(id) != null);
        return id;
    }

    /**
     * Allows the connections used by the server to be subclassed.
     * This can be useful for storage per connection without an additional lookup.
     */
    protected Connection newConnection(){
        return new Connection();
    }

    void updateConnections(){
        long time = Time.millis();
        for(Connection connection : connections){
            // Both false if closed
            if(connection.tcp.isTimedOut(time)) connection.close(DcReason.timeout);
            else if(connection.tcp.needsKeepAlive(time)) connection.sendTCP(FrameworkMessage.keepAlive);
            if(!connection.isConnected()) removeConnection(connection);
            else if(connection.isIdle()) connection.notifyIdle();
        }

        Seq<Connection> expired = null;
        for(Iterator<Connection> iter = pendingConnections.values(); iter.hasNext();){
            Connection connection = iter.next();
            int timeout = connection.tcp.timeoutMillis;
            if(!connection.isConnected()){
                iter.remove();
                connection.dispose();
            }else if(timeout > 0 && time - connection.acceptTime > timeout){
                if(expired == null) expired = new Seq<>();
                expired.add(connection);
            }
        }
        if(expired != null) expired.each(c -> c.close(DcReason.timeout));
    }

    protected boolean isPending(int connectionID){
        return pendingConnections.containsKey(connectionID);
    }

    protected void addConnection(Connection connection){
        int id = connection.getID();
        connections = Structs.add(connections, connection);
        IntMap<Connection> map = new IntMap<>(connectionsMap);
        map.put(id, connection);
        connectionsMap = map;
        if(connection.udpRemoteAddress == null) return;
        udpAddressToConnection.put(connection.udpRemoteAddress, connection);
    }

    /** The connection will be disposed, it must not be used after that. */
    protected void removeConnection(Connection connection){
        connection.dispose();
        int id = connection.getID();
        connections = Structs.remove(connections, connection);
        IntMap<Connection> map = connectionsMap;
        if(map.get(id) == connection){
            map = new IntMap<>(map);
            map.remove(id);
            connectionsMap = map;
        }
        InetSocketAddress address = connection.udpRemoteAddress;
        if(address != null && udpAddressToConnection.get(address) == connection)
            udpAddressToConnection.remove(address);
    }

    protected void clearConnections(){
        connections = new Connection[0];
        connectionsMap = new IntMap<>();
        pendingConnections.clear();
        udpAddressToConnection.clear();
    }

    public Connection getConnection(int connectionID){
        return connectionsMap.get(connectionID);
    }

    public Connection getConnectionIndex(int index){
        return connections[index];
    }

    public int getConnectionsSize(){
        return connections.length;
    }

    /** Returns the connections array. MUST NOT BE MODIFIED! */
    public Connection[] getConnections(){
        return connections;
    }

    public void eachConnections(Cons<Connection> consumer){
        Structs.each(consumer, connections);
    }

    public void eachConnections(Boolf<Connection> predicate, Cons<Connection> consumer){
        Structs.each(predicate, consumer, connections);
    }

    public void sendToAllTCP(Object object){
        sendToAllExceptTCP(0, object);
    }

    /** Sends the object over TCP to every connection except the specified one, serializing it only once. */
    public void sendToAllExceptTCP(int connectionID, Object object){
        sendToAll(connections, connectionID, object, true);
    }

    /** Sends the object over TCP to every connection in the list, serializing it only once. */
    public void sendToAllTCP(Object object, Iterable<Connection> connections){
        sendToAll(connections, 0, object, true);
    }

    public void sendToTCP(int connectionID, Object object){
        Connection connection = getConnection(connectionID);
        if(connection != null) connection.sendTCP(object);
    }

    /**
     * Sends the object over UDP to every connection, serializing it only once.
     * TCP only connections are ignored.
     */
    public void sendToAllUDP(Object object){
        sendToAllExceptUDP(0, object);
    }

    /**
     * Sends the object over UDP to every connection except the specified one, serializing it only once.
     * TCP only connections are ignored.
     */
    public void sendToAllExceptUDP(int connectionID, Object object){
        sendToAll(connections, connectionID, object, false);
    }

    /**
     * Sends the object over UDP to every connection in the list, serializing it only once.
     * The TCP only connections are ignored.
     */
    public void sendToAllUDP(Object object, Iterable<Connection> connections){
        sendToAll(connections, 0, object, false);
    }

    /** Sends the object to every connection except the specified one ({@code 0} for none), serializing it only once. */
    public void sendToAll(Connection[] connections, int exceptID, Object object, boolean reliable){
        if(connections.length == 0 || !reliable && udp == null) return;
        ByteBuffer buffer = serialize(object, reliable);
        if(buffer == null) return;

        try{
            for(Connection connection : connections){
                if(connection.getID() != exceptID) sendBuffer(connection, buffer, reliable);
            }
        }finally{
            ByteBufferPool.free(buffer);
        }
    }

    /** Sends the object to every connection in the list, serializing it only once. */
    public void sendToAll(Iterable<Connection> connections, int exceptID, Object object, boolean reliable){
        if(!reliable && udp == null) return;
        ByteBuffer buffer = serialize(object, reliable);
        if(buffer == null) return;

        try{
            for(Connection connection : connections){
                if(connection.getID() != exceptID) sendBuffer(connection, buffer, reliable);
            }
        }finally{
            ByteBufferPool.free(buffer);
        }
    }

    private static void sendBuffer(Connection connection, ByteBuffer buffer, boolean tcp){
        if(!connection.isConnected()) return;
        if(tcp) connection.sendTCPBuffer(buffer);
        else if(!connection.tcpOnly) connection.sendUDPBuffer(buffer);
    }

    public void sendToUDP(int connectionID, Object object){
        Connection connection = getConnection(connectionID);
        if(connection != null) connection.sendUDP(object);
    }

    protected ByteBuffer serialize(Object object, boolean tcp){
        if(object == null) throw new IllegalArgumentException("object cannot be null.");
        ByteBuffer buffer = ByteBufferPool.get().obtain(writeBufferSize, directBuffers);
        try{
            if(tcp) TcpConnection.write(serializer, buffer, object);
            else UdpConnection.write(serializer, buffer, object);
            buffer.flip();
            return buffer;
        }catch(ArcNetException ex){
            ByteBufferPool.free(buffer);
            ArcNet.handleError(ex);
            return null;
        }
    }

    /**
     * {@inheritDoc}
     * <p>
     * Should be called before connect().
     */
    @Override
    public void addListener(NetListener listener){
        dispatchListener.addListener(listener);
    }

    @Override
    public void removeListener(NetListener listener){
        dispatchListener.removeListener(listener);
    }

    /**
     * Closes all open connections and the server port(s).
     */
    @Override
    public void close(){
        synchronized(updateLock){
            Structs.each(Server::disposeQuietly, connections);
            pendingConnections.values().toSeq().each(Server::disposeQuietly);
            clearConnections();

            Streams.close(serverChannel);
            serverChannel = null;

            if(discoveryReceiver != null){
                discoveryReceiver.close();
                discoveryReceiver = null;
            }

            UdpConnection udp = this.udp;
            if(udp != null){
                this.udp = null;
                udp.dispose();
            }

            // Select one last time to complete closing the sockets.
            selector.wakeup();
            keysChanged = true;
            try{
                selector.selectNow();
            }catch(IOException | ClosedSelectorException ignored){}
        }
    }

    /** A listener failing must not prevent closing and releasing connections. */
    private static void disposeQuietly(Connection connection){
        try{
            connection.close(DcReason.closed);
        }catch(RuntimeException ex){
            ArcNet.handleError(ex);
        }
        connection.dispose();
    }

    /**
     * Releases the resources used by this server, which may no longer be used.
     */
    @Override
    public void dispose(){
        try{
            close();
        }catch(RuntimeException ex){
            // A listener can fail, but everything must still be released
            ArcNet.handleError(ex);
        }finally{
            shutdown = true;
            synchronized(updateLock){
                Streams.close(selector);
            }
        }
    }

    @Override
    public Thread getUpdateThread(){
        return updateThread;
    }

    @Override
    public NetSerializer getSerialization(){
        return serializer;
    }


    // I don't care about deprecation here, as the socket system methods won't be removed
    // it really doesn't matter if the multicast works or not
    @SuppressWarnings("deprecation")
    public class DiscoveryReceiver{
        volatile MulticastSocket socket = null;
        volatile boolean closed;
        Thread multicastThread;

        public void close(){
            closed = true;
            if(multicastThread != null) multicastThread.interrupt();
            MulticastSocket socket = this.socket;
            if(socket != null) socket.close();
        }

        public void start(){
            multicastThread = Threads.daemon("Server Multicast Discovery", () -> {
                try(MulticastSocket socket = new MulticastSocket(multicastPort)){
                    this.socket = socket;
                    if(closed) return; // Closed in the meantime
                    socket.joinGroup(multicastGroup);
                    DatagramPacket packet = new DatagramPacket(new byte[512], 512);
                    while(true){
                        socket.receive(packet);
                        reply(socket, packet);
                    }
                }catch(IOException e){
                    if(!closed) ArcNet.handleError(e);
                }
            });
        }

        /** A failing handler must not stop the discovery. */
        private void reply(MulticastSocket socket, DatagramPacket packet){
            try{
                discoveryHandler.onDiscoverReceived(packet.getAddress(), buffer -> {
                    DatagramPacket out;
                    if(buffer.hasArray()){
                        out = new DatagramPacket(buffer.array(), buffer.arrayOffset() + buffer.position(), buffer.remaining());
                    }else{
                        byte[] data = new byte[buffer.remaining()];
                        buffer.duplicate().get(data);
                        out = new DatagramPacket(data, data.length);
                    }
                    out.setSocketAddress(packet.getSocketAddress());
                    socket.send(out);
                });
            }catch(Exception e){
                if(!closed) ArcNet.handleError(e);
            }
        }
    }

    public interface ServerConnectFilter{
        boolean accept(InetSocketAddress address);
    }
}
