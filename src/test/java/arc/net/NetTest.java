// By Claude Opus 5.5
package arc.net;

import arc.net.FrameworkMessage.*;
import org.junit.*;

import java.io.*;
import java.net.*;
import java.nio.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;

import static org.junit.Assert.*;

/** Loopback tests of the client/server. In package arc.net to access some internals. */
public class NetTest{
    static final int timeout = 5000;

    /** Serializes framework messages and strings. */
    static class TestSerializer implements NetSerializer{
        @Override
        public void write(ByteBuffer buffer, Object o){
            if(o instanceof FrameworkMessage){
                buffer.put((byte)-2);
                if(o instanceof Ping){
                    buffer.put((byte)0).putInt(((Ping)o).id).put((byte)(((Ping)o).isReply ? 1 : 0));
                }else if(o instanceof DiscoverHost){
                    buffer.put((byte)1);
                }else if(o instanceof KeepAlive){
                    buffer.put((byte)2);
                }else if(o instanceof RegisterUDP){
                    buffer.put((byte)3).putInt(((RegisterUDP)o).connectionID);
                }else if(o instanceof RegisterTCP){
                    buffer.put((byte)4).putInt(((RegisterTCP)o).connectionID);
                }
            }else if(o instanceof String){
                byte[] bytes = ((String)o).getBytes(StandardCharsets.UTF_8);
                buffer.put((byte)1).putShort((short)bytes.length).put(bytes);
            }else throw new IllegalArgumentException("unknown object: " + o);
        }

        @Override
        public Object read(ByteBuffer buffer){
            byte id = buffer.get();
            if(id == -2){
                byte type = buffer.get();
                switch(type){
                    case 0: Ping p = new Ping(); p.id = buffer.getInt(); p.isReply = buffer.get() == 1; return p;
                    case 1: return FrameworkMessage.discoverHost;
                    case 2: return FrameworkMessage.keepAlive;
                    case 3: RegisterUDP u = new RegisterUDP(); u.connectionID = buffer.getInt(); return u;
                    case 4: RegisterTCP t = new RegisterTCP(); t.connectionID = buffer.getInt(); return t;
                    default: throw new IllegalArgumentException("unknown framework message: " + type);
                }
            }
            if(id == 1){
                byte[] bytes = new byte[buffer.getShort()];
                buffer.get(bytes);
                return new String(bytes, StandardCharsets.UTF_8);
            }
            throw new IllegalArgumentException("unknown id: " + id);
        }
    }

    /** Records events of a listener. */
    static class Recorder implements NetListener{
        final BlockingQueue<Connection> connected = new LinkedBlockingQueue<>(), disconnected = new LinkedBlockingQueue<>();
        final BlockingQueue<String> received = new LinkedBlockingQueue<>();

        @Override
        public void connected(Connection connection){ connected.add(connection); }
        @Override
        public void disconnected(Connection connection, DcReason reason){ disconnected.add(connection); }
        @Override
        public void received(Connection connection, Object object){
            if(object instanceof String) received.add((String)object);
        }
    }

    Server server;
    Client client;
    Recorder serverEvents, clientEvents;
    int tcpPort, udpPort;
    final ConcurrentLinkedQueue<Throwable> errors = new ConcurrentLinkedQueue<>();
    arc.func.Cons<Throwable> oldHandler;

    @Before
    public void setUp() throws Exception{
        oldHandler = ArcNet.errorHandler;
        ArcNet.errorHandler = errors::add;
        try(ServerSocket s = new ServerSocket(0); DatagramSocket d = new DatagramSocket(0)){
            tcpPort = s.getLocalPort();
            udpPort = d.getLocalPort();
        }
        server = newServer();
        serverEvents = new Recorder();
        server.addListener(serverEvents);
        server.bind(tcpPort, udpPort);
        server.start();

        client = new Client(8192, 8192, new TestSerializer());
        clientEvents = new Recorder();
        client.addListener(clientEvents);
        client.start();
    }

    protected Server newServer(){
        return new Server(8192, 8192, new TestSerializer());
    }

    @After
    public void tearDown(){
        try{ client.dispose(); }catch(Throwable ignored){}
        try{ server.stop(); server.dispose(); }catch(Throwable ignored){}
        ArcNet.errorHandler = oldHandler;
    }

    static <T> T poll(BlockingQueue<T> queue) throws InterruptedException{
        T value = queue.poll(timeout, TimeUnit.MILLISECONDS);
        assertNotNull("timed out", value);
        return value;
    }

    static void waitFor(java.util.function.BooleanSupplier condition) throws InterruptedException{
        long end = System.currentTimeMillis() + timeout;
        while(!condition.getAsBoolean()){
            assertTrue("timed out", System.currentTimeMillis() < end);
            Thread.sleep(20);
        }
    }

    @Test
    public void tcpAndUdp() throws Exception{
        client.connect(timeout, "127.0.0.1", tcpPort, udpPort);
        Connection con = poll(serverEvents.connected);
        assertTrue(con.isUDPConnected());
        assertFalse(con.isTCPOnly());
        assertEquals(1, server.getConnectionsSize());

        client.sendTCP("hello tcp");
        assertEquals("hello tcp", poll(serverEvents.received));
        client.sendUDP("hello udp");
        assertEquals("hello udp", poll(serverEvents.received));

        // serialized once, sent to all
        server.sendToAllTCP("bulk tcp");
        assertEquals("bulk tcp", poll(clientEvents.received));
        server.sendToAllUDP("bulk udp");
        assertEquals("bulk udp", poll(clientEvents.received));
        server.sendToTCP(con.getID(), "direct");
        assertEquals("direct", poll(clientEvents.received));

        client.close();
        assertSame(con, poll(serverEvents.disconnected));
        // removed and disposed by the update thread
        waitFor(() -> server.getConnectionsSize() == 0 && con.isDisposed());
        assertNull(server.getConnection(con.getID()));
        // sending to a disposed connection must not throw
        assertEquals(0, con.sendTCP("nope"));
    }

    /** The bulk sends to a list only send to the connections of the list. */
    @Test
    public void bulkSendToList() throws Exception{
        client.connect(timeout, "127.0.0.1", tcpPort, udpPort);
        Connection first = poll(serverEvents.connected);
        Client other = new Client(8192, 8192, new TestSerializer());
        Recorder otherEvents = new Recorder();
        other.addListener(otherEvents);
        other.start();
        try{
            other.connect(timeout, "127.0.0.1", tcpPort, udpPort);
            poll(serverEvents.connected);

            server.sendToAllTCP("tcp list", java.util.Collections.singletonList(first));
            server.sendToAllUDP("udp list", java.util.Collections.singletonList(first));
            // TCP and UDP can be received in any order
            java.util.Set<String> received = new java.util.HashSet<>();
            received.add(poll(clientEvents.received));
            received.add(poll(clientEvents.received));
            assertEquals(new java.util.HashSet<>(java.util.Arrays.asList("tcp list", "udp list")), received);
            assertNull("sent to a connection not in the list", otherEvents.received.poll(300, TimeUnit.MILLISECONDS));
        }finally{
            other.dispose();
        }
    }

    @Test
    public void tcpOnlyRejectedByDefault() throws Exception{
        try{
            client.connect(timeout, "127.0.0.1", tcpPort);
        }catch(IOException ignored){} // Can be closed before the end of connect()
        poll(serverEvents.disconnected);
        assertNull("connected must not be fired", serverEvents.connected.poll(200, TimeUnit.MILLISECONDS));
        waitFor(() -> !client.isConnected() && server.getConnectionsSize() == 0);
    }

    @Test
    public void tcpOnlyClient() throws Exception{
        server.allowTcpOnlyConnections(true);
        client.connect(timeout, "127.0.0.1", tcpPort);
        Connection con = poll(serverEvents.connected);
        assertTrue(con.isTCPOnly());
        assertFalse(con.isUDPConnected());
        assertTrue(client.isConnected());

        // A TCP only connection is ignored by the UDP broadcasts, without being closed,
        // and sending over UDP fails, like with Arc
        server.sendToAllUDP("udp ignored");
        assertNull(clientEvents.received.poll(200, TimeUnit.MILLISECONDS));
        assertTrue(con.isConnected());
        try{
            client.sendUDP("udp without udp");
            fail("sendUDP without UDP must throw");
        }catch(IllegalStateException expected){}

        // Registering again must not add the connection twice
        client.sendTCP(new RegisterTCP());
        client.sendTCP("after register");
        assertEquals("after register", poll(serverEvents.received));
        assertEquals(1, server.getConnectionsSize());
        assertNull("connected fired twice", serverEvents.connected.poll(200, TimeUnit.MILLISECONDS));
    }

    @Test
    public void reconnectTcpOnlyAfterUdp() throws Exception{
        server.allowTcpOnlyConnections(true);
        client.connect(timeout, "127.0.0.1", tcpPort, udpPort);
        poll(serverEvents.connected);
        client.close();
        poll(serverEvents.disconnected);

        client.connect(timeout, "127.0.0.1", tcpPort);
        Connection con = poll(serverEvents.connected);
        assertTrue(con.isTCPOnly());
        assertTrue(client.isConnected());
        client.sendTCP("still works");
        assertEquals("still works", poll(serverEvents.received));
    }

    /** The length prefix must be big-endian, whatever the native order is. */
    @Test
    public void wireFormatIsBigEndian() throws Exception{
        try(Socket socket = new Socket("127.0.0.1", tcpPort)){
            socket.setSoTimeout(timeout);
            DataInputStream in = new DataInputStream(socket.getInputStream());
            byte[] header = new byte[2];
            in.readFully(header);
            // RegisterTCP: id (1) + type (1) + connection id (4)
            assertEquals(0, header[0]);
            assertEquals(6, header[1]);
            assertEquals(-2, in.readByte());
            assertEquals(4, in.readByte());
        }
    }

    /** A connection that never completes its registration must be closed. */
    @Test
    public void pendingRegistrationTimeout() throws Exception{
        server.dispose();
        server = new Server(8192, 8192, new TestSerializer()){
            @Override
            protected Connection newConnection(){
                return new Connection(){
                    @Override
                    void initialize(NetSerializer serialization, int writeBufferSize, int objectBufferSize, boolean direct){
                        super.initialize(serialization, writeBufferSize, objectBufferSize, direct);
                        setTimeout(300);
                    }
                };
            }
        };
        server.bind(tcpPort, udpPort);
        server.start();

        try(Socket socket = new Socket("127.0.0.1", tcpPort)){
            socket.setSoTimeout(timeout);
            InputStream in = socket.getInputStream();
            // Read the registration then wait, the server must close the socket
            byte[] buffer = new byte[64];
            int read;
            long start = System.currentTimeMillis();
            while((read = in.read(buffer)) != -1){
                assertTrue("not closed", System.currentTimeMillis() - start < timeout);
            }
            assertEquals(-1, read);
        }catch(SocketException e){
            // connection reset is fine too
        }
    }

    /** An invalid UDP registration must not close the UDP channel shared by every connections. */
    @Test
    public void invalidUdpRegistrationKeepsUdpWorking() throws Exception{
        int id;
        try(Socket socket = new Socket("127.0.0.1", tcpPort)){
            socket.setSoTimeout(timeout);
            DataInputStream in = new DataInputStream(socket.getInputStream());
            in.readFully(new byte[4]); // length + framework ids
            id = in.readInt();

            // Register the UDP from another address than the TCP one
            DatagramSocket other;
            try{
                other = new DatagramSocket(new InetSocketAddress("127.0.0.2", 0));
            }catch(IOException e){
                Assume.assumeNoException("127.0.0.2 is not available", e);
                return;
            }
            try(DatagramSocket udp = other){
                ByteBuffer data = ByteBuffer.allocate(16);
                RegisterUDP register = new RegisterUDP();
                register.connectionID = id;
                new TestSerializer().write(data, register);
                udp.send(new DatagramPacket(data.array(), data.position(), new InetSocketAddress("127.0.0.1", udpPort)));
            }
            Thread.sleep(300);

            // UDP must still work for a normal client
            client.connect(timeout, "127.0.0.1", tcpPort, udpPort);
            poll(serverEvents.connected);
            client.sendUDP("udp alive");
            assertEquals("udp alive", poll(serverEvents.received));
        }
    }

    /** Closing from another thread than the update one must not dispose connections still used by it. */
    @Test
    public void stopFromAnotherThread() throws Exception{
        client.connect(timeout, "127.0.0.1", tcpPort, udpPort);
        Connection con = poll(serverEvents.connected);
        server.stop();
        poll(clientEvents.disconnected);
        assertSame(con, poll(serverEvents.disconnected));
        waitFor(() -> con.isDisposed());
        assertEquals(0, server.getConnectionsSize());
        for(Throwable t : errors){
            assertFalse("unexpected error: " + t, t instanceof IllegalStateException || t instanceof NullPointerException);
        }
    }
}
