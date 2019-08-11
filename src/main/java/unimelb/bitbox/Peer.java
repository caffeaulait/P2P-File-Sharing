package unimelb.bitbox;

import unimelb.bitbox.util.Configuration;
import unimelb.bitbox.util.Document;
import unimelb.bitbox.util.HostPort;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.net.*;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.InvalidKeySpecException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

/**
 * the main class
 */
public class Peer {

    private static Logger log = Logger.getLogger(Peer.class.getName());
    //the list of peers in the config file
    static ArrayList<String> peers = new ArrayList<>();
    //the peers that are connected, including both incoming and outgoing
    volatile static HashMap<String, Socket> connectingPeers = new HashMap<>();
    //the incoming peers that are connected
    volatile static HashMap<String, Socket> incomingPeers = new HashMap<>();
    //local port number
     static int TcpPort = Integer.parseInt(Configuration.getConfigurationValue("port"));
    //Allocate a thread for each established connection
    static ThreadPoolExecutor threadPool = new ThreadPoolExecutor(50, 100, 60, TimeUnit.SECONDS, new LinkedBlockingQueue<>());
    //
    static ServerMain sm;
    //localhostname
    static String myName = Configuration.getConfigurationValue("advertisedName");
    //TCP/UDP mode
    static String MODE = Configuration.getConfigurationValue("mode");
    //
    static int UdpPort = Integer.parseInt(Configuration.getConfigurationValue("udpPort"));
    //
    static final HashMap<String, String> rememberingPeer = new HashMap<>();
    //public keys and identities stored in the config file.
    static final HashMap<String, RSAPublicKey> publicKeys = new HashMap<>();
    //
    static final HashMap<String, String> UdpPeers = new HashMap<>();
    //
    static final HashMap<String, String> UdpBlack = new HashMap<>();
    //
    static final HashMap<String, String> requestedUdp = new HashMap<>();
    //
    static final HashMap<String, Socket> requestedTcp = new HashMap<>();
    //
    public static int clientPort = Integer.parseInt(Configuration.getConfigurationValue("clientPort"));


    public static void main(String[] args) throws IOException, NumberFormatException, NoSuchAlgorithmException {
        System.setProperty("java.util.logging.SimpleFormatter.format",
                "[%1$tc] %2$s %4$s: %5$s%n");

        log.info("BitBox Peer starting...");

        Configuration.getConfiguration();

        Collections.addAll(peers, Configuration.getConfigurationValue("peers").split(","));

        String[] keys  = Configuration.getConfigurationValue("authorized_keys").split(",");

        try{
            for (String key : keys){
                String identity = key.split(" ")[2];
                RSAPublicKey publicKey = GetKeyPair.getPubfromString(key);
                publicKeys.put(identity,publicKey);
            }
        }catch(InvalidKeySpecException e){
            log.info("invalid key");
        }


        sm = new ServerMain();

        new Thread(() ->
                TcpServer())
                .start();

        if (MODE.equals("tcp")) {
            new Thread(() ->
                    TcpListening(sm))
                    .start();

            new Thread(() ->
                    TcpAsking(sm))
                    .start();

            new Thread(() ->
                    PeriodicSyncTcp(sm))
                    .start();
        } else if (MODE.equals("udp")) {

            new Thread(() -> {
                try {
                    UdpListening();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }).start();


            new Thread(() -> {
                try {
                    UdpAsking();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }).start();

            new Thread(() -> {
                try {
                    PeriodicSyncUdp(sm);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }).start();

        }
    }

    /**
     * the peer will keep attempting to connect to all peers in the configuration file
     */
    public static void TcpAsking(ServerMain sm) {
        while (peers.size() > 0) {
            for (Iterator<String> i = peers.iterator(); i.hasNext(); ) {
                try {
                    String hostport = i.next();
                    String host = hostport.split(":")[0];
                    int port = Integer.parseInt(hostport.split(":")[1]);
//                log.info("Connected to peer "+peer);
                    Socket socket = new Socket(host, port);
                    i.remove();
                    System.out.println("Try connect to peer: " + hostport);
                    BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), "UTF-8"));
                    MessageGenerator MG = new MessageGenerator();
                    Document handShakeRequest = MG.HandshakeRequest("port");
                    bw.write(handShakeRequest.toJson());
                    bw.newLine();
                    bw.flush();
                    threadPool.execute(new TcpProcessor(socket, sm));

                } catch (UnknownHostException e) {
                    System.out.println(e.getMessage());
                } catch (IOException e) {
//                    log.info("Peer Offline");
                }
            }
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * the peer will listen on its local port, waiting other peers to connect
     */
    public static void TcpListening(ServerMain sm) {
        try {
            ServerSocket ss = new ServerSocket(TcpPort);
//                System.out.println("local port is：" + port);
            while (true) {
                Socket socket = ss.accept();
                threadPool.execute(new TcpProcessor(socket, sm));
            }
        } catch (IOException e) {
//                log.warning(e.getMessage());
            System.out.println(e.getMessage());
        }
    }

    /**
     * Every syncInterval seconds, the BitBox Peer should call generateSyncEvents()
     * to do a general synchronization with all neighboring peers.
     */
    public static void PeriodicSyncTcp(ServerMain sm) {
        while (true) {
            try {
                for (Socket socket : connectingPeers.values()) {
                    sm.synchronize(sm.fileSystemManager.generateSyncEvents(), socket);
                }
                Thread.sleep(Integer.parseInt(Configuration.getConfigurationValue("syncInterval")) * 1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    public static void PeriodicSyncUdp(ServerMain sm) {
        while (true) {
            try {
                String host;
                InetAddress address;
                int port = 0;
                synchronized (UdpPeers) {
                    for (String hostPort : UdpPeers.keySet()) {
                        host = hostPort.split(":")[0];
                        address = InetAddress.getByName(host);
                        port = Integer.parseInt(hostPort.split(":")[1]);
                        sm.synchronize(sm.fileSystemManager.generateSyncEvents(), host, port);
                        Thread.sleep(Integer.parseInt(Configuration.getConfigurationValue("syncInterval")) * 1000);
                    }
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            } catch (UnknownHostException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * The peer that tried to connect should do a breadth first search of peers in the peers list, attempt to make a connection to one of them.
     *
     * @param peers the peer list returned from the CONNECTION_REFUSED protocol
     */
    public static void TcpBFS(ArrayList<Document> peers, ServerMain sm) {
        for (Document peer : peers) {
            HostPort hostPort = new HostPort(peer);
            String host = hostPort.host;
            int port = hostPort.port;
            if(!Peer.requestedTcp.keySet().contains(host + ":" + port)) {
                try {
                    Socket socket = new Socket(host, port);
                    System.out.println("Try connect to peer: " + hostPort.toString());
                    BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), "UTF-8"));
                    MessageGenerator MG = new MessageGenerator();
                    Document handShakeRequest = MG.HandshakeRequest("port");
                    bw.write(handShakeRequest.toJson());
                    bw.newLine();
                    bw.flush();
                    threadPool.execute(new TcpProcessor(socket, sm));
                    Peer.requestedTcp.put(host + ":" + port, socket);
                    break;
                } catch (UnknownHostException e) {
//            log.warning(e.getMessage());
                    System.out.println(e.getMessage());
                } catch (IOException e) {
//            log.warning(e.getMessage());
                }
            }
        }
    }

    public static void UdpBFS(ArrayList<Document> peers, ServerMain sm) {
        for (Document peer : peers) {
            HostPort hostPort = new HostPort(peer);
            String host = hostPort.host;
            int port = hostPort.port;
            if (!Peer.requestedUdp.keySet().contains(host + ":" + port)) {
                try {
                    InetAddress address = InetAddress.getByName(host);
                    MessageGenerator MG = new MessageGenerator();
                    Document handShakeRequest = MG.HandshakeRequest("udpPort");
                    byte[] data = handShakeRequest.toJson().getBytes();

                    DatagramPacket packet = new DatagramPacket(data, data.length, address, port);
                    DatagramSocket socket = new DatagramSocket();
                    socket.send(packet);
                    Thread tt = new Thread(new UdpProcessor(socket, sm));
                    tt.start();
                    Thread.sleep(100);

                    Peer.requestedUdp.put(host + ":" + port, "11");
                    break;
                } catch (UnknownHostException e) {
//            log.warning(e.getMessage());
                    System.out.println(e.getMessage());
                } catch (IOException e) {
//            log.warning(e.getMessage());
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    public static void UdpAsking() {
        try {
            String host;
            InetAddress address;
            int port = 0;
            for (String hostPort : peers) {
                host = hostPort.split(":")[0];
                address = InetAddress.getByName(host);
                port = Integer.parseInt(hostPort.split(":")[1]);
                MessageGenerator MG = new MessageGenerator();
                Document handShakeRequest = MG.HandshakeRequest("udpPort");
                byte[] data = handShakeRequest.toJson().getBytes();

                DatagramPacket packet = new DatagramPacket(data, data.length, address, port);
                DatagramSocket socket = new DatagramSocket();
                socket.send(packet);

                Thread tt = new Thread(new UdpProcessor(socket, sm));
                tt.start();
                Thread.sleep(100);
            }
        } catch (UnknownHostException e) {
            e.printStackTrace();
        } catch (SocketException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    public static void TcpServer() {
        try {
            ServerSocket ss = new ServerSocket(clientPort);
            while (true) {
                Socket socket = ss.accept();
                new Thread (new PeerServer(socket)).start();
            }
        } catch (IOException e) {
            System.out.println(e.getMessage());
        }
    }

    public static void UdpListening() throws Exception{
        DatagramSocket socket = new DatagramSocket(UdpPort);
        log.info("UDP Peer initiated, waiting for messages...");
        Thread thread = new Thread(new UdpProcessor(socket, sm));
        thread.start();
        Thread.sleep(100);
    }

}