package unimelb.bitbox;


import unimelb.bitbox.util.Configuration;
import unimelb.bitbox.util.Document;
import unimelb.bitbox.MessageGenerator;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Base64;

public class UdpProcessor implements Runnable {
    DatagramSocket sk;
    DatagramPacket pk;
    ServerMain sm;
    Document buffer;
    static String lastSendMsg;
    long receiveCount=0;
    static long ByteNum = 0;

    public UdpProcessor(DatagramSocket ds, ServerMain content) {
        this.sk = ds;
        this.sm = content;
    }

    @Override
    public void run() {
        try {
            while(true) {
                pk = new DatagramPacket(new byte[8192], 8192);
                sk.receive(pk);
                receiveCount += 1;
                String info = new String(pk.getData(), 0, pk.getLength());
                System.out.println("Message Received： " + info);
                Document MsgReceived = Document.parse(info);
                processCommand(MsgReceived, sm, pk, sk);
            }
            } catch (IOException e) {
        e.printStackTrace();
    }
    }

    public void processCommand(Document MsgReceived, ServerMain sm, DatagramPacket pk, DatagramSocket sk) {
        long blockSize = Long.parseLong(Configuration.getConfigurationValue("blockSize"));
        MessageGenerator responseGenerator = new MessageGenerator();
        Document response;

        String command = MsgReceived.getString("command");

        String pathName = null;
        boolean pathSafe = false;
        boolean fileExist = false;
        boolean dirExist = false;
        boolean fileSame = false;
        Document fileDescriptor = new Document();
        String md5 = null;
        long fileSize = -1;
        long lastModified = -1;

        if (MsgReceived.containsKey("pathName")) {
            pathName = MsgReceived.getString("pathName");
            pathSafe = sm.fileSystemManager.isSafePathName(pathName);
            fileExist = sm.fileSystemManager.fileNameExists(pathName);
            dirExist = sm.fileSystemManager.dirNameExists(pathName);
        }

        if (MsgReceived.containsKey("fileDescriptor")) {
            fileDescriptor = (Document) MsgReceived.get("fileDescriptor");
            md5 = fileDescriptor.getString("md5");
            if (fileExist) fileSame = sm.fileSystemManager.fileNameExists(pathName, md5);
            fileSize = fileDescriptor.getLong("fileSize");
            lastModified = fileDescriptor.getLong("lastModified");
        }

        try {
            switch (command) {
                case "INVALID_PROTOCOL": {
                    //close the connection
                    if (MsgReceived.containsKey("hp")) {
                        Peer.UdpPeers.remove(MsgReceived.getString("hp"));
                    }
                    break;
                }
                case "CONNECTION_REFUSED": {
                    Peer.UdpBFS((ArrayList<Document>) MsgReceived.get("peers"), sm);
                    break;
                }
                case "HANDSHAKE_REQUEST": {
                    //get the foreign host and port from handshake message
                    Document hostPort = (Document) MsgReceived.get("hostPort");
                    int port = (int) hostPort.getLong("port");
                    String host = hostPort.getString("host");
                    String hp = host+":"+port;
                    if (Peer.UdpBlack.keySet().contains(hp)) {
                        response = responseGenerator.InvalidProtocol();
                        write(response.toJson(), pk, sk);
                        break;
                    }
                    if(Peer.rememberingPeer.size()>=Integer.parseInt(Configuration.getConfigurationValue("maximumIncommingConnections")) && !Peer.rememberingPeer.keySet().contains(hp)){
                        response = responseGenerator.UdpAskingMax(Peer.UdpPeers);
                        write(response.toJson(), pk, sk);
                    } else {
                        if (!Peer.rememberingPeer.keySet().contains(hp)){
                            Peer.rememberingPeer.put(hp, hp);
                        }
                        if (!Peer.UdpPeers.keySet().contains(hp)) {
                            Peer.UdpPeers.put(hp, hp);
                        }
                        try {
                            Thread.sleep(500);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                        response = responseGenerator.HandshakeResponse();
                        write(response.toJson(), pk, sk);
                        //initial synchronization
                        sm.synchronize(sm.fileSystemManager.generateSyncEvents(), host, port);
                    }
                    break;
                }
                case "HANDSHAKE_RESPONSE": {
                    //get the foreign host and port from handshake message
                    Document hostPort = (Document) MsgReceived.get("hostPort");
                    int port = (int) hostPort.getLong("port");
                    String host = hostPort.getString("host");
                    String hp = host+":"+port;
                    if (!Peer.UdpPeers.keySet().contains(hp)) {
                        Peer.UdpPeers.put(hp, hp);
                    }
                    try {
                        Thread.sleep(500);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    //Add it into existing connected peers
                    sm.synchronize(sm.fileSystemManager.generateSyncEvents(), host, port);
                    break;
                }
                case "FILE_CREATE_REQUEST": {
                    boolean status;
                    boolean createLoader = false;
                    //create file loader if path is safe and the file does not exist
                    if (pathSafe && !fileExist) {
                        try {
                            createLoader = sm.fileSystemManager.createFileLoader(pathName, md5, fileSize, lastModified);
                        } catch (IOException e) {
                            e.printStackTrace();
                            System.out.println("Error in FILE_CREATE_REQUEST");
                        } catch (NoSuchAlgorithmException e) {
                            e.printStackTrace();
                            System.out.println("Error in FILE_CREATE_REQUEST");
                        }
                        status = createLoader;
                    } else {
                        status = false;
                    }
                    //sending file create response
                    response = responseGenerator.FileCreateResponse(MsgReceived, status, pathSafe, fileExist);
                    write(response.toJson(), pk, sk);

                    //sending filebytes request
                    if (status) {
                        //make a local copy
                        try {
                            if (sm.fileSystemManager.checkShortcut(pathName)) {
                                //                        System.out.println("local copy can be used");
                                break;
                            }
                        } catch (NoSuchAlgorithmException e) {
                            e.printStackTrace();
                            System.out.println("Failed checkShortCut");
                        }
                        long position = 0;
                        long length = Math.min(blockSize, fileDescriptor.getLong("fileSize"));
                        response = responseGenerator.FileBytesRequest(pathName, fileDescriptor, position, length);
                        lastSendMsg = response.toJson();
                        write(response.toJson(), pk, sk);
//                        new Thread(new ByteResponseTimer(ByteNum, pk, sk, lastSendMsg)).start();

                        //if files have different content with same pathname
                    } else if (pathSafe && !fileSame) {
                        if (sm.fileSystemManager.modifyFileLoader(pathName, md5, lastModified)) {
                            long position = 0;
                            long length = Math.min(blockSize, fileDescriptor.getLong("fileSize"));
                            response = responseGenerator.FileBytesRequest(pathName, fileDescriptor, position, length);
                            lastSendMsg = response.toJson();
                            write(response.toJson(), pk, sk);
//                            new Thread(new ByteResponseTimer(ByteNum, pk, sk, lastSendMsg)).start();
                        }
                    }
                    break;
                }
                case "FILE_CREATE_RESPONSE": {
                    //
                    break;
                }
                case "FILE_DELETE_REQUEST": {
                    boolean status;
                    if (pathSafe && fileExist) {
                        status = sm.fileSystemManager.deleteFile(pathName, lastModified, md5);
                    } else {
                        status = false;
                    }
                    //send a file delete response
                    response = responseGenerator.FileDeleteResponse(MsgReceived, status, pathSafe, fileExist);
                    write(response.toJson(), pk, sk);
                    break;
                }
                case "FILE_DELETE_RESPONSE": {
                    //
                    break;
                }
                case "FILE_MODIFY_REQUEST": {
                    boolean status;
                    boolean modifyLoader = false;
                    if (pathSafe && fileExist && !fileSame) {
                        try {
                            modifyLoader = sm.fileSystemManager.modifyFileLoader(pathName, md5, lastModified);
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                        status = modifyLoader;
                    } else {
                        status = false;
                    }
                    //send a file modify response
                    response = responseGenerator.FileModifyResponse(MsgReceived, status, pathSafe, fileExist, fileSame);
                    write(response.toJson(), pk, sk);

                    if (status) {
                        //
                        long position = 0;
                        long length = Math.min(blockSize, fileDescriptor.getLong("fileSize"));
                        response = responseGenerator.FileBytesRequest(pathName, fileDescriptor, position, length);
                        lastSendMsg = response.toJson();
                        write(response.toJson(), pk, sk);
//                        new Thread(new ByteResponseTimer(ByteNum, pk, sk, lastSendMsg)).start();
                    }
                    break;
                }
                case "FILE_MODIFY_RESPONSE": {
                    //
                    break;
                }
                case "DIRECTORY_CREATE_REQUEST": {
                    boolean status;
                    if (pathSafe && !dirExist) {
                        status = sm.fileSystemManager.makeDirectory(pathName);
                    } else {
                        status = false;
                    }
                    //send a directory create response
                    response = responseGenerator.DirCreateResponse(MsgReceived, status, pathSafe, dirExist);
                    write(response.toJson(), pk, sk);
                    break;
                }
                case "DIRECTORY_CREATE_RESPONSE": {
                    //
                    break;
                }
                case "DIRECTORY_DELETE_REQUEST": {
                    boolean status;
                    if (pathSafe && dirExist) {
                        status = sm.fileSystemManager.deleteDirectory(pathName);
                    } else {
                        status = false;
                    }
                    //send a directory delete response
                    response = responseGenerator.DirDeleteResponse(MsgReceived, status, pathSafe, dirExist);
                    write(response.toJson(), pk, sk);
                    break;
                }
                case "DIRECTORY_DELETE_RESPONSE": {
                    //
                    break;
                }
                case "FILE_BYTES_REQUEST": {
                    long position = MsgReceived.getLong("position");
                    long length = MsgReceived.getLong("length");
                    String content = "";
                    boolean status;
                    try {
                        ByteBuffer fileData = sm.fileSystemManager.readFile(md5, position, length);
                        content = Base64.getEncoder().encodeToString(fileData.array());
                        status = true;
                    } catch (NoSuchAlgorithmException e) {
                        status = false;
                    }
                    //send a fileBytes response
                    response = responseGenerator.FileBytesResponse(MsgReceived, content, status);
                    write(response.toJson(), pk, sk);
                    break;
                }
                case "FILE_BYTES_RESPONSE": {
                    ByteNum += 1;
                    long position = MsgReceived.getLong("position");
                    long length = MsgReceived.getLong("length");
                    if (MsgReceived.getBoolean("status")) {
                        String content = MsgReceived.getString("content");
                        byte[] conByte = Base64.getDecoder().decode(content);
                        ByteBuffer fileData = ByteBuffer.wrap(conByte);
                        boolean writeOK;
                        try {
                            writeOK = sm.fileSystemManager.writeFile(pathName, fileData, position);
                        } catch (IOException e) {
                            writeOK = false;
                        }
                        if (writeOK) {
                            //file is transmitted partially
                            if (length + position < fileDescriptor.getLong("fileSize")) {
                                position = length + position;
                                length = Math.min(blockSize, fileSize - position);
                                response = responseGenerator.FileBytesRequest(pathName, fileDescriptor, position, length);
                                lastSendMsg = response.toJson();
                                write(response.toJson(), pk, sk);
                                new Thread(new ByteResponseTimer(ByteNum, pk, sk, lastSendMsg)).start();
                            } else {
                                try {
                                    if (sm.fileSystemManager.checkWriteComplete(pathName)) {
                                        //whole file fully written
                                        sm.fileSystemManager.cancelFileLoader(pathName);
                                    } else {
                                        System.out.println("file write failed");
                                    }
                                } catch (NoSuchAlgorithmException e) {
                                    System.out.println("Failed checkWriteComplete");
                                }
                            }
                        }
                    }
                    break;
                }
                default: {
                    response = responseGenerator.InvalidProtocol();
                    write(response.toJson(), pk, sk);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static long write (String res, DatagramPacket pk, DatagramSocket sk) {
        try {
            byte[] reply = res.getBytes();
            DatagramPacket packet = new DatagramPacket(reply, reply.length, pk.getAddress(), pk.getPort());
            sk.send(packet);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return System.currentTimeMillis();
    }

    public static void writeUdp (String res, InetAddress host, int port, DatagramSocket sk) {
        try {
            byte[] reply = res.getBytes();
            DatagramPacket packet = new DatagramPacket(reply, reply.length, host, port);
            sk.send(packet);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
