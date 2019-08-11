package unimelb.bitbox;

import unimelb.bitbox.util.Configuration;
import unimelb.bitbox.util.Document;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.io.*;
import java.net.*;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.InvalidKeySpecException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;

public class PeerServer implements Runnable {
    private Socket ss;
    private SecretKey serverSk;
    private boolean connecting = true;
    public PeerServer(Socket socket) {
        this.ss=socket;
    }

    @Override
    public void run() {
        try {
            BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(ss.getOutputStream(), "UTF-8"));
            BufferedReader br = new BufferedReader(new InputStreamReader(ss.getInputStream(), "UTF-8"));
            while (connecting) {
                try {
                    String reader = br.readLine();
                    if (reader != null) {
                        Document MsgReceived = Document.parse(reader);
//                    log.info("Message Received: "+MsgReceived.toJson());
                        System.out.println("Message Received: " + MsgReceived.toJson());
                        if (MsgReceived.getString("command")!=null) {
                            String identity = MsgReceived.getString("identity");
                            System.out.println(identity);
                            if (Peer.publicKeys.containsKey(identity)) {
                                RSAPublicKey publicKey = Peer.publicKeys.get(identity);
                                byte[] raw = AES.generateKey();
                                serverSk = new SecretKeySpec(raw, 0, raw.length, "AES");
                                String Base64encted = Base64.getEncoder().encodeToString(GetKeyPair.encryptByPub(publicKey, raw));
                                Document res = ClientMessage.successResponse(Base64encted);
                                ClientProcessor.writeTcp(bw, res.toJson());
                            } else {
                                Document res = ClientMessage.failResponse();
                                ClientProcessor.writeTcp(bw, res.toJson());
                            }
                        } else {
                            String payload = AES.decryptbyAES(serverSk, MsgReceived.getString("payload"));
                            Document Msg = Document.parse(payload);
//                            System.out.println("decrypted payload： "+ Msg.toJson());
                            response(Msg, bw);
                        }
                    }
                } catch (NoSuchAlgorithmException e) {
                    System.out.println("Failed get pub");
                } catch (InvalidKeySpecException e) {
                    System.out.println("Invalid pub key");
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    public void response (Document Msg, BufferedWriter bw) {
        String cmd = Msg.getString("command");
        if (Peer.MODE.equals("tcp")) {
            switch (cmd) {
                case "LIST_PEERS_REQUEST": {
                    String output = null;
                    try {
                        output = ClientMessage.payloadMessage(ClientMessage.listTCPResponse(Peer.connectingPeers), serverSk).toJson();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    ClientProcessor.writeTcp(bw, output);
                    break;
                }
                case "CONNECT_PEER_REQUEST": {
                    String host = Msg.getString("host");
                    int port = (int) Msg.getLong("port");
                    try {
                        Socket newSocket = new Socket(host, port);
                        BufferedWriter newBw = new BufferedWriter(new OutputStreamWriter(newSocket.getOutputStream(), "UTF-8"));
                        MessageGenerator MG = new MessageGenerator();
                        Document handShakeRequest = MG.HandshakeRequest("port");
                        newBw.write(handShakeRequest.toJson());
                        newBw.newLine();
                        newBw.flush();
                        Peer.threadPool.execute(new TcpProcessor(newSocket, Peer.sm));
                        try {
                            Thread.sleep(500);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                        System.out.println(Peer.connectingPeers);

                        synchronized (Peer.connectingPeers) {
                            boolean status = Peer.connectingPeers.containsKey(host + ":" + port);
                            String output = ClientMessage.payloadMessage(ClientMessage.connectPeerResponse(host, port, status), serverSk).toJson();
                            ClientProcessor.writeTcp(bw, output);
                        }
                    } catch (UnknownHostException e) {
                        e.printStackTrace();
                    } catch (UnsupportedEncodingException e) {
                        e.printStackTrace();
                    } catch (IOException e) {
                        e.printStackTrace();
                    } catch (Exception e) {
                        System.out.println("Failed encrypt payload");
                    }
                    break;
                }
                case "DISCONNECT_PEER_REQUEST": {
                    String host = Msg.getString("host");
                    int port = (int) Msg.getLong("port");
                    boolean status = Peer.connectingPeers.containsKey(host+":"+port);
                    String output = null;
                    if (status) {
                        try {
                            Socket newSocket = Peer.connectingPeers.get(host+":"+port);
                            BufferedWriter newBw = new BufferedWriter(new OutputStreamWriter(newSocket.getOutputStream(), "UTF-8"));
                            MessageGenerator MG = new MessageGenerator();
                            Document invalid = MG.InvalidProtocol();
                            newBw.write(invalid.toJson());
                            newBw.newLine();
                            newBw.flush();
                        } catch (UnknownHostException e) {
                            e.printStackTrace();
                        } catch (UnsupportedEncodingException e) {
                            e.printStackTrace();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                    Peer.connectingPeers.remove(host+":"+port);

                    try {
                        output = ClientMessage.payloadMessage(ClientMessage.disconnectPeerResponse(host, port, status), serverSk).toJson();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    ClientProcessor.writeTcp(bw, output);
                    break;
                }
                default:
            }
        } else if (Peer.MODE.equals("udp")) {
            switch (cmd) {
                case "LIST_PEERS_REQUEST": {
                    String output = null;
                    try {
                        output = ClientMessage.payloadMessage(ClientMessage.listUDPResponse(Peer.UdpPeers), serverSk).toJson();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    ClientProcessor.writeTcp(bw, output);
                    break;
                }
                case "CONNECT_PEER_REQUEST": {
                    try {
                        String host = Msg.getString("host");
                        InetAddress address = InetAddress.getByName(host);
                        int port = (int) Msg.getLong("port");
                        String hp = host + ":" + port;
                        Peer.UdpBlack.remove(hp);

                        Thread.sleep(500);

                        MessageGenerator MG = new MessageGenerator();
                        Document handShakeRequest = MG.HandshakeRequest("udpPort");
                        byte[] data = handShakeRequest.toJson().getBytes();

                        DatagramPacket packet = new DatagramPacket(data, data.length, address, port);
                        DatagramSocket socket = new DatagramSocket();
                        socket.send(packet);

                        Thread t = new Thread(new UdpProcessor(socket, Peer.sm));
                        t.start();

                        Thread.sleep(500);

                        synchronized (Peer.UdpPeers) {
                            boolean status = Peer.UdpPeers.containsKey(host + ":" + port);
                            String output = ClientMessage.payloadMessage(ClientMessage.connectPeerResponse(host, port, status), serverSk).toJson();
                            ClientProcessor.writeTcp(bw, output);
                        }

                    } catch (UnknownHostException e) {
                        e.printStackTrace();
                    } catch (SocketException e) {
                        e.printStackTrace();
                    } catch (IOException e) {
                        e.printStackTrace();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    break;
                }
                case "DISCONNECT_PEER_REQUEST": {
                    String host = Msg.getString("host");
                    int port = (int) Msg.getLong("port");
                    String hp = host + ":" + port;

                    try {
                        InetAddress address = InetAddress.getByName(host);
                        MessageGenerator MG = new MessageGenerator();
                        Document handShakeRequest = MG.InvalidUdp(Peer.myName+":"+Peer.UdpPort);
                        byte[] data = handShakeRequest.toJson().getBytes();

                        DatagramPacket packet = new DatagramPacket(data, data.length, address, port);
                        DatagramSocket socket = new DatagramSocket();
                        socket.send(packet);
                    } catch (UnknownHostException e) {
                        e.printStackTrace();
                    } catch (SocketException e) {
                        e.printStackTrace();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }

                    boolean status = Peer.UdpPeers.containsKey(hp);
                    Peer.UdpPeers.remove(host+":"+port);
                    Peer.UdpBlack.put(hp, hp);
                    try {
                        Thread.sleep(500);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    String output = null;
                    try {
                        output = ClientMessage.payloadMessage(ClientMessage.disconnectPeerResponse(host, port, status), serverSk).toJson();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    ClientProcessor.writeTcp(bw, output);
                    break;
                }
            }
        }
    }
}
