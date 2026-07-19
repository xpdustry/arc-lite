package arc.net;

import arc.func.Cons;
import arc.math.*;
import arc.net.FrameworkMessage.*;
import arc.struct.*;
import arc.util.*;
import arc.util.pooling.ByteBufferPool;

import java.io.*;
import java.net.*;
import java.nio.channels.*;
import java.util.*;

/**
 * Manages TCP and optionally UDP connections from many {@linkplain Client Clients}.
 * @author Nathan Sweet <misc@n4te.com>
 */
public class Server implements EndPoint{
    protected final NetSerializer serializer;
    protected final int writeBufferSize, objectBufferSize;
    protected final boolean directBuffers;

    private final Selector selector;
    private int emptySelects;
    private ServerSocketChannel serverChannel;
    private UdpConnection udp;

    private final IntMap<Connection> connectionsMap = new IntMap<>();
    private final Seq<Connection> connections = new Seq<>(false);
    private volatile Connection[] stales = null; // Used to thread-safely remove a connection
    private final ObjectMap<InetSocketAddress, Connection> udpAddressToConnection = new ObjectMap<>();
    private final IntMap<Connection> pendingConnections = new IntMap<>();
    private final Rand rand = new Rand();

    private volatile boolean shutdown = true, starting;
    private final Object updateLock = new Object();
    private Thread updateThread;

    protected int multicastPort = 21010;
    protected InetAddress multicastGroup;
    protected DiscoveryReceiver discoveryReceiver;
    protected ServerDiscoveryHandler discoveryHandler;
    private ServerConnectFilter connectFilter;

    protected final DispatchListener dispatchListener = new DispatchListener(){
        @Override
        public void disconnected(Connection connection, DcReason reason){
            addStale(connection);
            super.disconnected(connection, reason);
        }
    };

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

    public void setMulticast(String group, int multicastPort){
        this.multicastPort = multicastPort;
        try{
            multicastGroup = InetAddress.getByName(group);
        }catch(IOException e){
            e.printStackTrace(); // rethrow?
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
        close();
        synchronized(updateLock){
            selector.wakeup();
            try{
                serverChannel = selector.provider().openServerSocketChannel();
                serverChannel.socket().bind(tcpPort);
                serverChannel.configureBlocking(false);
                serverChannel.register(selector, SelectionKey.OP_ACCEPT);

                if(udpPort != null){
                    udp = new UdpConnection(serializer, objectBufferSize, directBuffers);
                    udp.bind(selector, udpPort);
                }

                if(multicastGroup != null && (udpPort == null || multicastPort != udpPort.getPort())){
                    discoveryReceiver = new DiscoveryReceiver();
                    discoveryReceiver.start();
                }
            }catch(IOException ex){
                close();
                throw ex;
            }
        }
    }

    /**
     * Accepts any new connections and reads or writes any pending data for the current connections.
     * @param timeout Wait for up to the specified milliseconds for a connection to be ready to process.
     * May be zero to return immediately if there are no connections to process.
     */
    @Override
    public void update(int timeout) throws IOException{
        updateThread = Thread.currentThread();
        // Blocks to avoid a select while the selector is used to bind the server connection.
        synchronized(updateLock){}

        clearStales(); // Clear any staling connections
        if (!select(timeout)) {
            updateConnections();
            return;
        }

        Set<SelectionKey> keys = selector.selectedKeys();
        synchronized(keys){
            for(Iterator<SelectionKey> iter = keys.iterator(); iter.hasNext();){
                SelectionKey selectionKey = iter.next();
                iter.remove();
                Connection fromConnection = (Connection)selectionKey.attachment();

                try{
                    // Possible since connection is already closed?
                    if(fromConnection != null && isStale(fromConnection)) continue;

                    boolean readUDP = doSelectTCP(selectionKey, fromConnection);
                    if (!readUDP) continue;

                    InetSocketAddress fromAddress;
                    try{
                        fromAddress = udp.readFromAddress();
                    }catch(IOException ex){
                        ArcNet.handleError(ex);
                        continue;
                    }
                    if(fromAddress == null) continue;
                    fromConnection = udpAddressToConnection.get(fromAddress);

                    doSelectUDP(selectionKey, fromConnection, fromAddress);
                }catch(CancelledKeyException ex){
                    if(fromConnection != null) fromConnection.close(DcReason.error);
                    else selectionKey.channel().close();
                }
            }
        }

        updateConnections();
    }

    boolean select(int timeout) throws IOException {
        long startTime = Time.nanos();
        int select = timeout > 0 ? selector.select(timeout) : selector.selectNow();
        if(select == 0){
            if(++emptySelects == 100){
                emptySelects = 0;
                // NIO freaks and returns immediately with 0 sometimes, so try to keep from hogging the CPU.
                long elapsedTime = Time.nanosToMillis(Time.nanos() - startTime);
                long maxWait = Math.min(25, timeout);
                try{
                    // Better yielding or using onSpinWait?
                    if(elapsedTime < maxWait) Thread.sleep(maxWait - elapsedTime);
                }catch(InterruptedException ignored){}
            }
            return false;
        }else{
            emptySelects = 0;
            return true;
        }
    }

    protected boolean doSelectTCP(SelectionKey selectionKey, Connection fromConnection) throws IOException {
        int ops = selectionKey.readyOps();
        UdpConnection udp = this.udp;

        if(fromConnection != null){ // Must be a TCP read or write operation.
            if((ops & SelectionKey.OP_READ) == SelectionKey.OP_READ){
                try{
                    while(true){
                        Object object = fromConnection.tcp.readObject();
                        if(object == null) break;
                        if(object instanceof RegisterTCP && udp != null && fromConnection.udpRemoteAddress == null){
                            pendingConnections.remove(fromConnection.getID());
                            fromConnection.tcpOnly = true;
                            addConnection(fromConnection);
                            fromConnection.notifyConnected();
                            break;
                        }
                        fromConnection.notifyReceived(object);
                    }
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
            return false;
        }

        if((ops & SelectionKey.OP_ACCEPT) == SelectionKey.OP_ACCEPT){
            ServerSocketChannel serverChannel = this.serverChannel;
            if(serverChannel == null) return false;
            try{
                SocketChannel socketChannel = serverChannel.accept();
                if(socketChannel != null) acceptOperation(socketChannel);
            }catch(IOException ex){
                ArcNet.handleError(ex);
            }
            return false;
        }

        // Must be a UDP read operation.
        if(udp == null){
            selectionKey.channel().close();
            return false;
        }

        return true;
    }

    protected void doSelectUDP(SelectionKey selectionKey, Connection fromConnection, InetSocketAddress fromAddress) throws IOException {
        UdpConnection udp = this.udp;

        // Drop object
        if(fromConnection != null){
            if(fromConnection.udpPaused){
                udp.readBuffer.clear();
                return;
            }
            if(fromConnection.tcpOnly){
                selectionKey.channel().close(); // Connection was registered as TCP only!
                return;
            }
        }

        Object object;
        try{
            object = udp.readObject();
        }catch(ArcNetException ex){
            ArcNet.handleError(new ArcNetException("Error reading UDP from connection: " + fromAddress, ex));
            return;
        }

        if(object instanceof FrameworkMessage){
            if(object instanceof RegisterUDP){
                // Store the fromAddress on the connection and reply over TCP
                // with a RegisterUDP to indicate success.
                int fromConnectionID = ((RegisterUDP)object).connectionID;
                Connection connection = pendingConnections.remove(fromConnectionID);
                if(connection == null || connection.udpRemoteAddress != null) return;
                // It is illegal to register an UDP connection without the same address as the TCP one
                // Or connection was registered as TCP only
                InetSocketAddress toAddress = connection.getRemoteAddressTCP();
                if(connection.tcpOnly || toAddress == null || !fromAddress.getAddress().equals(toAddress.getAddress())){
                    pendingConnections.put(fromConnectionID, connection);
                    selectionKey.channel().close();
                    return;
                }

                connection.udpRemoteAddress = fromAddress;
                addConnection(connection);
                connection.sendTCP(object);
                connection.notifyConnected();
                return;
            }

            if(object instanceof DiscoverHost){
                try{
                    discoveryHandler.onDiscoverReceived(
                        fromAddress.getAddress(),
                        buff -> udp.datagramChannel.send(buff, fromAddress)
                    );
                }catch(IOException ignored){}
                return;
            }
        }

        if(fromConnection == null) return;
        fromConnection.notifyReceived(object);
    }

    DcReason closeReason(String errMsg){
        return errMsg != null && errMsg.contains("closed") ? DcReason.closed : DcReason.error;
    }

    void updateConnections() {
        long time = Time.millis();
        for(int i = 0; i < connections.size; i++){
            Connection connection = connections.get(i);
            if(connection.tcp.isTimedOut(time)) connection.close(DcReason.timeout);
            else if(connection.tcp.needsKeepAlive(time)) connection.sendTCP(FrameworkMessage.keepAlive);
            if(isStale(connection)) continue; // Avoid more events if its a stale connection
            if(connection.isIdle()) connection.notifyIdle();
        }
    }

    public void keepAlive(){
        long time = Time.millis();
        for(int i = 0; i < connections.size; i++){
            Connection connection = connections.get(i);
            if(connection.tcp.needsKeepAlive(time)) connection.sendTCP(FrameworkMessage.keepAlive);
        }
    }

    @Override
    public void run(){
        shutdown = starting = false;
        try {
            while(!shutdown){
                try{
                    update(250);
                }catch(IOException ex){
                    close();
                }
            }
        }catch(Exception e){
            close();
            throw e;
        }finally {
            shutdown = true;
        }
    }

    @Override
    public void start(){
        if (starting) return;
        starting = true;
        // Try to let any previous update thread stop.
        if(updateThread != null){
            shutdown = true;
            try{
                updateThread.join(5000);
            }catch(InterruptedException ignored){}
        }
        Threads.thread("Server", this);
    }

    @Override
    public void stop(){
        if(shutdown) return;
        close();
        starting = false;
        shutdown = true;
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
            try{
                String address = ((InetSocketAddress)socketChannel.getRemoteAddress()).getAddress().getHostAddress();
                if(!connectFilter.accept(address)){
                    socketChannel.close();
                    return;
                }
            }catch(IOException ignored){}
        }

        Connection connection = newConnection();
        connection.initialize(serializer, writeBufferSize, objectBufferSize, directBuffers);
        connection.endPoint = this;
        UdpConnection udp = this.udp;
        if(udp != null) connection.udp = udp;

        try{
            SelectionKey selectionKey = connection.tcp.accept(selector, socketChannel);
            selectionKey.attach(connection);

            int id = generateId();
            connection.id = id;
            connection.setConnected(true);
            connection.addListener(dispatchListener);

            if(udp == null) addConnection(connection);
            else pendingConnections.put(id, connection);

            RegisterTCP registerConnection = new RegisterTCP();
            registerConnection.connectionID = id;
            connection.sendTCP(registerConnection);

            if(udp == null) connection.notifyConnected();
        }catch(IOException ex){
            connection.close(DcReason.error);
        }
    }

    protected int generateId(){
        int id;
        do{
            id = rand.nextInt();
        }while(id == 0 || pendingConnections.containsKey(id) ||
               getConnection(id) != null || isStale(id));
        return id;
    }

    /**
     * Allows the connections used by the server to be subclassed.
     * This can be useful for storage per connection without an additional lookup.
     */
    protected Connection newConnection(){
        return new Connection();
    }

    private void addStale(Connection con){
        if(con == null) return;
        Connection[] stales = this.stales;
        this.stales = stales == null ? new Connection[] {con} : Structs.add(stales, con);
    }

    private boolean isStale(Connection con){
        if(con == null) return false;
        Connection[] stales = this.stales;
        return stales != null && Structs.contains(stales, con::equals);
    }

    private boolean isStale(int connectionID){
        Connection[] stales = this.stales;
        if(stales == null) return false;
        for(Connection connection : stales){
            if(connection.getID() == connectionID) return true;
        }
        return false;
    }

    private void clearStales(){
        Connection[] stales = this.stales;
        this.stales = null;
        if(stales == null) return;
        Structs.each(this::removeConnection, stales);
    }

    protected void addConnection(Connection connection){
        connections.add(connection);
        connectionsMap.put(connection.getID(), connection);
        if(connection.udpRemoteAddress == null) return;
        udpAddressToConnection.put(connection.udpRemoteAddress, connection);
    }

    /** The connection will be disposed, it must not be used after that. */
    protected void removeConnection(Connection connection){
        connection.dispose();
        connections.remove(connection);
        connectionsMap.remove(connection.getID());
        pendingConnections.remove(connection.getID());
        if(connection.udpRemoteAddress == null) return;
        udpAddressToConnection.remove(connection.udpRemoteAddress);
    }

    protected void clearConnections(){
        connections.clear();
        connectionsMap.clear();
        pendingConnections.clear();
        udpAddressToConnection.clear();
    }

    public Connection getConnection(int connectionID){
        return connectionsMap.get(connectionID);
    }

    public Connection getConnectionIndex(int index){
        return connections.get(index);
    }

    public int getConnectionsSize(){
        return connections.size;
    }

    public void eachConnections(Cons<Connection> consumer) {
        connections.each(consumer);
    }

    // TODO: Provide mechanism for sending to multiple clients without serializing multiple times.

    public void sendToAllTCP(Object object){
        for(int i = 0; i < connections.size; i++){
            connections.get(i).sendTCP(object);
        }
    }

    public void sendToAllExceptTCP(int connectionID, Object object){
        for(int i = 0; i < connections.size; i++){
            Connection connection = connections.get(i);
            if(connection.getID() != connectionID) connection.sendTCP(object);
        }
    }

    public void sendToTCP(int connectionID, Object object){
        Connection connection = getConnection(connectionID);
        if(connection != null) connection.sendTCP(object);
    }

    public void sendToAllUDP(Object object){
        for(int i = 0; i < connections.size; i++){
            connections.get(i).sendUDP(object);
        }
    }

    public void sendToAllExceptUDP(int connectionID, Object object){
        for(int i = 0; i < connections.size; i++){
            Connection connection = connections.get(i);
            if(connection.getID() != connectionID) connection.sendUDP(object);
        }
    }

    public void sendToUDP(int connectionID, Object object){
        Connection connection = getConnection(connectionID);
        if(connection != null) connection.sendUDP(object);
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
    public void close(){
        eachConnections(Connection::dispose);
        clearConnections();
        stales = null;

        ServerSocketChannel serverChannel = this.serverChannel;
        if(serverChannel != null){
            try{
                serverChannel.close();
            }catch(IOException ignored){}
            this.serverChannel = null;
        }

        if(discoveryReceiver != null){
            discoveryReceiver.close();
            discoveryReceiver = null;
        }

        UdpConnection udp = this.udp;
        if(udp != null){
            udp.dispose();
            this.udp = null;
        }

        // Blocks to avoid a select while the selector is used to bind the server connection.
        synchronized(updateLock){}

        // Select one last time to complete closing the socket.
        selector.wakeup();
        try{
            selector.selectNow();
        }catch(IOException ignored){}
    }

    /**
     * Releases the resources used by this server, which may no longer be used.
     */
    @Override
    public void dispose(){
        close();
        try{
            selector.close();
        }catch(IOException ignored){}
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
        MulticastSocket socket = null;
        Thread multicastThread;

        public void close(){
            try{
                if(multicastThread != null) multicastThread.interrupt();
                if(socket == null) return;
                socket.leaveGroup(multicastGroup);
                socket.close();
            }catch(IOException e){
                ArcNet.handleError(e);
            }
        }

        public void start(){
            multicastThread = Threads.daemon("Server Multicast Discovery", () -> {
                try{
                    socket = new MulticastSocket(multicastPort);
                    socket.joinGroup(multicastGroup);
                    DatagramPacket packet = new DatagramPacket(new byte[512], 512);
                    while(true){
                        socket.receive(packet);
                        discoveryHandler.onDiscoverReceived(packet.getAddress(), buffer -> {
                            DatagramPacket out = new DatagramPacket(buffer.array(), buffer.arrayOffset(), buffer.remaining());
                            out.setSocketAddress(packet.getSocketAddress());
                            socket.send(out);
                        });
                    }
                }catch(IOException e){
                    if(!(e instanceof SocketException && "Socket closed".equals(e.getMessage()))){
                        ArcNet.handleError(e);
                    }
                }
            });
        }
    }

    public interface ServerConnectFilter{
        boolean accept(String address);
    }
}
