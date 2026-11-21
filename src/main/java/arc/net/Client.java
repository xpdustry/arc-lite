

package arc.net;

import arc.func.*;
import arc.net.FrameworkMessage.*;
import arc.util.*;
import arc.util.io.Streams;
import arc.util.pooling.ByteBufferPool;

import java.io.*;
import java.net.*;
import java.nio.*;
import java.nio.channels.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Represents a TCP and optionally a UDP connection to a {@link Server}.
 * @author Nathan Sweet <misc@n4te.com>
 */
public class Client extends Connection implements EndPoint{
    protected final NetSerializer serialization;
    private Selector selector;
    private int emptySelects;
    private long emptySelectsStart;

    protected volatile boolean tcpRegistered, udpRegistered;
    private final Object tcpRegistrationLock = new Object();
    private final Object udpRegistrationLock = new Object();

    protected volatile boolean shutdown = true, starting, connecting;
    private final Object updateLock = new Object();
    private volatile Thread updateThread;

    protected int connectTimeout;
    protected InetAddress connectHost;
    protected int connectTcpPort;
    protected int connectUdpPort;

    private int readsPerSelect = 16; // Same as Netty
    private static ExecutorService discoverExecutor;
    private Prov<DatagramPacket> discoveryPacket = () -> new DatagramPacket(new byte[256], 256);

    public Client(int writeBufferSize, int objectBufferSize, NetSerializer serialization){
        this(writeBufferSize, objectBufferSize, false, serialization);
    }

    /**
     * @param writeBufferSize One buffer of this size is allocated.
     * Objects are serialized to the write buffer where the bytes are queued
     * until they can be written to the TCP socket.
     * <p>
     * Normally the socket is writable and the bytes are written immediately.
     * If the socket cannot be written to and enough serialized objects are
     * queued to overflow the buffer, then the connection will be closed.
     * <p>
     * The write buffer should be sized at least as large as the  largest object
     * that will be sent, plus some head room to allow for some serialized
     * objects to be queued in case the buffer is temporarily not writable.
     * The amount of head room needed is dependent upon the size of objects being
     * sent and how often they are sent.
     *
     * @param objectBufferSize One (using only TCP) or three (using both TCP and
     * UDP) buffers of this size are allocated.
     * These buffers are used to hold the bytes for a single object graph until it
     * can be sent over the network or deserialized.
     * <p>
     * The object buffers should be sized at least as large as the largest object
     * that will be sent or received.
     */
    public Client(int writeBufferSize, int objectBufferSize, boolean directBuffers, NetSerializer serialization){
        endPoint = this;
        this.serialization = serialization;

        initialize(serialization, writeBufferSize, objectBufferSize, directBuffers);

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
    public void setReadsPerSelect(int maxReads){
        readsPerSelect = maxReads < 1 ? Integer.MAX_VALUE : maxReads;
    }

    public void setDiscoveryPacket(Prov<DatagramPacket> discoveryPacket){
        this.discoveryPacket = discoveryPacket;
    }

    /**
     * Opens a TCP only client.
     * @see #connect(int, InetAddress, int, int)
     */
    public void connect(int timeout, String host, int tcpPort) throws IOException{
        connect(timeout, InetAddress.getByName(host), tcpPort, -1);
    }

    /**
     * Opens a TCP and UDP client.
     * @see #connect(int, InetAddress, int, int)
     */
    public void connect(int timeout, String host, int tcpPort, int udpPort) throws IOException{
        connect(timeout, InetAddress.getByName(host), tcpPort, udpPort);
    }

    /**
     * Opens a TCP only client.
     * @see #connect(int, InetAddress, int, int)
     */
    public void connect(int timeout, InetAddress host, int tcpPort) throws IOException{
        connect(timeout, host, tcpPort, -1);
    }

    /**
     * Opens a TCP and UDP client.
     * Blocks until the connection is complete or the timeout is reached.
     * <p>
     * Because the framework must perform some minimal communication before the
     * connection is considered successful, {@link #update(int)} must be called
     * on a separate thread during the connection process.
     * @throws IllegalStateException if called from the connection's update thread.
     * @throws IOException if the client could not be opened or connecting times out.
     */
    public void connect(int timeout, InetAddress host, int tcpPort, int udpPort) throws IOException{
        if(host == null) throw new IllegalArgumentException("host cannot be null.");
        checkDisposed();
        if(Thread.currentThread() == getUpdateThread())
            throw new IllegalStateException("Cannot connect on the connection's update thread.");
        this.connectTimeout = timeout;
        this.connectHost = host;
        this.connectTcpPort = tcpPort;
        this.connectUdpPort = udpPort;
        close();
        id = 0;
        connecting = true;
        try{
            long endTime;
            synchronized(updateLock){
                UdpConnection udp = this.udp;
                if(udpPort == -1){
                    this.udp = null;
                    if(udp != null) udp.dispose();
                }else if(udp == null){
                    this.udp = new UdpConnection(serialization, tcp.readBuffer.capacity(), tcp.readBuffer.isDirect());
                }

                tcpRegistered = false;
                selector.wakeup();
                endTime = Time.nanos() + Time.millisToNanos(timeout);
                tcp.connect(selector, new InetSocketAddress(host, tcpPort), timeout);
            }

            awaitRegistration(false, endTime, null,
            "Connected, but timed out during TCP registration.\n"
            + "Note: Client#update must be called in a separate thread during connect.");

            if(udpPort == -1) return;
            InetSocketAddress udpAddress = new InetSocketAddress(host, udpPort);
            synchronized(updateLock){
                udpRegistered = false;
                selector.wakeup();
                udp.connect(selector, udpAddress);
            }

            awaitRegistration(true, endTime, udpAddress, "Connected, but timed out during UDP registration: " + host + ":" + udpPort);
        }catch(IOException ex){
            close();
            throw ex;
        }finally{
            connecting = false;
        }
    }

    private void awaitRegistration(boolean udp, long endTime, InetSocketAddress udpAddress, String timeoutMessage) throws IOException{
        Object lock = udp ? udpRegistrationLock : tcpRegistrationLock;
        synchronized(lock){
            while(!isRegistered(udp) && connecting && Time.nanos() - endTime < 0){
                if(udp){
                    RegisterUDP registerUDP = new RegisterUDP();
                    registerUDP.connectionID = id;
                    this.udp.send(registerUDP, udpAddress);
                }
                Threads.waitQuietly(lock, 100);
            }
            if(!isRegistered(udp)){
                if(!connecting) throw new SocketException("Connection closed while connecting.");
                throw new SocketTimeoutException(timeoutMessage);
            }
        }
    }

    private boolean isRegistered(boolean udp){
        return udp ? udpRegistered : tcpRegistered;
    }

    /**
     * Calls {@link #connect(int, InetAddress, int, int) connect} with the
     * values last passed to connect.
     * @throws IllegalStateException if connect has never been called.
     */
    public void reconnect() throws IOException{
        reconnect(connectTimeout);
    }

    /**
     * Calls {@link #connect(int, InetAddress, int, int) connect} with the
     * specified timeout and the other values last passed to connect.
     * @throws IllegalStateException if connect has never been called.
     */
    public void reconnect(int timeout) throws IOException{
        if(connectHost == null) throw new IllegalStateException("This client has never been connected.");
        connect(timeout, connectHost, connectTcpPort, connectUdpPort);
    }

    /**
     * Reads or writes any pending data for this client.
     * Must always be called by the same dedicated thread..
     * @param timeout Wait for up to the specified milliseconds for data
     * to be ready to process.
     * May be zero to return immediately if there is no data to process.
     */
    public void update(int timeout) throws IOException{
        updateThread = Thread.currentThread();
        Set<SelectionKey> keys = select(timeout);

        synchronized(updateLock){
            if(keys != null){
                for(Iterator<SelectionKey> iter = keys.iterator(); selector.isOpen() && iter.hasNext();){
                    SelectionKey selectionKey = iter.next();
                    iter.remove();

                    try{
                        doSelect(selectionKey);
                    }catch(CancelledKeyException ignored){
                        // Connection is closed.
                    }
                }
            }

            updateConnection();
        }
    }


    Set<SelectionKey> select(int timeout) throws IOException{
        if((timeout > 0 ? selector.select(timeout) : selector.selectNow()) == 0){
            // NIO freaks and returns immediately with 0 sometimes, so try to keep from hogging the CPU.
            long now = Time.nanos();
            if(emptySelects++ == 0) emptySelectsStart = now;
            else if(emptySelects >= 100){
                emptySelects = 0;
                // Only wait if it's happening within 2ms
                if(timeout > 0 && now - emptySelectsStart < 2_000_000L) Threads.sleepQuietly(1);
            }
        }else emptySelects = 0;
        Set<SelectionKey> keys = selector.selectedKeys();
        return keys.isEmpty() ? null : keys;
    }

    protected void doSelect(SelectionKey selectionKey) throws IOException{
        int ops = selectionKey.readyOps();

        if((ops & SelectionKey.OP_READ) == SelectionKey.OP_READ){
            if(selectionKey.attachment() == tcp){
                int i = 0;
                do{
                    while(doSelectTCP(tcp));
                }while(i++ < readsPerSelect && tcp.readSocket());

            }else{
                UdpConnection udp = this.udp;
                if(udp == null) return; // TCP only
                for(int i = 0; i < readsPerSelect && udp.readFromAddress() != null; i++){
                    doSelectUDP(udp);
                }
            }
        }

        if((ops & SelectionKey.OP_WRITE) == SelectionKey.OP_WRITE) {
            tcp.writeOperation();
        }
    }

    protected boolean doSelectTCP(TcpConnection tcp) throws IOException{
        Object object = tcp.readObject();
        if (object == null) return false;

        UdpConnection udp = this.udp;
        //TODO: check the TCP+UDP validation returned by the server?
        if(!tcpRegistered){
            if(object instanceof RegisterTCP){
                id = ((RegisterTCP)object).connectionID;
                // We only want TCP, resend the packet to finish connecting
                if(udp == null) tcp.send(object);
                registered(false, udp == null);
            }
            return true;
        }else if(udp != null && !udpRegistered){
            if(object instanceof RegisterUDP) registered(true, true);
            return true;
        }

        notifyReceived(object);
        return true;
    }

    /** Wakes up {@link #awaitRegistration}, and notifies the listeners. */
    private void registered(boolean udp, boolean connected){
        Object lock = udp ? udpRegistrationLock : tcpRegistrationLock;
        synchronized(lock){
            if(udp) udpRegistered = true;
            else tcpRegistered = true;
            lock.notifyAll();
            if(connected) setConnected(true);
        }
        if(connected) notifyConnected();
    }

    protected boolean doSelectUDP(UdpConnection udp){
        Object object = udp.readObject();
        if (object == null) return false;
        notifyReceived(object);
        return true;
    }

    protected void updateConnection() {
        if(!isConnected) return;
        long time = Time.millis();
        if(tcp.isTimedOut(time)) close(DcReason.timeout);
        else keepAlive(time);
        if(isIdle()) notifyIdle();
    }

    public void keepAlive(){
        keepAlive(Time.millis());
    }

    void keepAlive(long time){
        if(!isConnected) return;
        if(tcp.needsKeepAlive(time)) sendTCP(FrameworkMessage.keepAlive);
        UdpConnection udp = this.udp;
        if(udp != null && udpRegistered && udp.needsKeepAlive(time)) sendUDP(FrameworkMessage.keepAlive);
    }

    public void handleNetException(ArcNetException ex){
        lastProtocolError = ex;
        close();
        throw ex;
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
                    close();
                }catch(ArcNetException ex){
                    handleNetException(ex);
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
        checkDisposed();
        Thread thread = updateThread;
        boolean alive = thread != null && thread.isAlive();
        if(alive && (!shutdown || thread == Thread.currentThread())){
            shutdown = false;
            return;
        }
        if(alive){
            selector.wakeup();
            Threads.joinQuietly(thread, 5000);
        }

        starting = true;
        shutdown = false;
        updateThread = Threads.daemon("Client", this);
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

    @Override
    public void close(){
        close(DcReason.closed);
    }

    @Override
    public void close(DcReason reason){
        connecting = false;
        super.close(reason);

        // Select one last time to complete closing the socket.
        // Repeated wakeups are ignored by the selector until its next select.
        selector.wakeup();
    }

    /**
     * Releases the resources used by this client, which may no longer be used.
     */
    @Override
    public void dispose(){
        if(disposed) return;
        disposed = true;
        try{
            stop();
            close();
        }catch(RuntimeException ex){
            ArcNet.handleError(ex);
        }finally{
            shutdown = true;
            synchronized(updateLock){
                tcp.dispose();
                UdpConnection udp = this.udp;
                if(udp != null) udp.dispose();
                Streams.close(selector);
            }
        }
    }

    /**
     * An empty object will be sent if the UDP connection is inactive more than
     * the specified milliseconds.
     * Network hardware may keep a translation table of inside to outside IP addresses
     * and a UDP keep alive keeps this table entry from expiring.
     * Set to zero to disable. Defaults to {@code 19000}.
     */
    public void setKeepAliveUDP(int keepAliveMillis){
        UdpConnection udp = this.udp;
        if(udp == null) throw new IllegalStateException("Not connected via UDP.");
        udp.keepAliveMillis = keepAliveMillis;
    }

    @Override
    public Thread getUpdateThread(){
        return updateThread;
    }

    @Override
    public NetSerializer getSerialization(){
        return serialization;
    }

    private void broadcast(int udpPort, DatagramSocket socket) throws IOException{
        ByteBuffer data = ByteBufferPool.getHeap(64);
        try{
            serialization.write(data, FrameworkMessage.discoverHost);
            data.flip();
            int len = data.remaining();

            for(NetworkInterface iface : Collections.list(NetworkInterface.getNetworkInterfaces())){
                if(!iface.isUp()) continue;

                for(InterfaceAddress baseAddress : iface.getInterfaceAddresses()){
                    InetAddress address = baseAddress.getBroadcast();
                    if(address == null) continue;
                    socket.send(new DatagramPacket(data.array(), data.arrayOffset(), len, address, udpPort));
                }
            }
        }finally{
            ByteBufferPool.free(data);
        }
    }

    /**
     * Broadcasts a UDP message on the LAN to discover any running servers.
     * @param udpPort The UDP port of the server.
     * @param timeoutMillis The number of milliseconds to wait for a response.
     */
    public void discoverHosts(int udpPort, String multicastGroup, int multicastPort, int timeoutMillis,
                              Cons<DatagramPacket> handler, Runnable done){
        if(discoverExecutor == null) discoverExecutor = Threads.unboundedExecutor("Server Discovery");
        AtomicInteger searches = new AtomicInteger(2);

        //broadcast
        discoverExecutor.submit(() -> {
            try(DatagramSocket socket = new DatagramSocket()){
                socket.setBroadcast(true);
                broadcast(udpPort, socket);
                receiveHosts(socket, timeoutMillis, handler);
                return null;
            }finally{
                if(searches.decrementAndGet() == 0) done.run();
            }
        });

        //multicast
        discoverExecutor.submit(() -> {
            ByteBuffer data = ByteBufferPool.getHeap(64);
            try(DatagramSocket socket = new DatagramSocket()){
                serialization.write(data, FrameworkMessage.discoverHost);
                data.flip();

                socket.send(new DatagramPacket(data.array(), data.arrayOffset(), data.remaining(),
                            InetAddress.getByName(multicastGroup), multicastPort));
                receiveHosts(socket, timeoutMillis, handler);
                return null;
            }finally{
                ByteBufferPool.free(data);
                if(searches.decrementAndGet() == 0) done.run();
            }
        });
    }

    /** Gives the received packets to the handler, until the timeout ends the search with an exception. */
    private void receiveHosts(DatagramSocket socket, int timeoutMillis, Cons<DatagramPacket> handler) throws IOException{
        socket.setSoTimeout(timeoutMillis);
        while(true){ //read until error?
            DatagramPacket packet = discoveryPacket.get();
            socket.receive(packet);
            handler.get(packet);
        }
    }
}
