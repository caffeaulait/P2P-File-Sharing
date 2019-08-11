package unimelb.bitbox;

import unimelb.bitbox.util.Configuration;
import unimelb.bitbox.util.Document;
import unimelb.bitbox.MessageGenerator;

import java.io.*;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.logging.Logger;

/**
 * this class handles all types of message received
 */
public class TcpProcessor implements Runnable {
    private static Logger log = Logger.getLogger(TcpProcessor.class.getName());
    private final Socket socket;
    private boolean connecting;
    private ServerMain sm;

    public TcpProcessor(Socket socket, ServerMain sm) {
        this.socket = socket;
        this.sm = sm;
        this.connecting = true;
    }


    @Override
    public void run() {
        try {
            BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), "UTF-8"));
            BufferedReader br = new BufferedReader(new InputStreamReader(socket.getInputStream(), "UTF-8"));
            while (connecting) {
                try {
                    String reader = br.readLine();
                    if (reader != null) {
                        Document MsgReceived = Document.parse(reader);
//                    log.info("Message Received: "+MsgReceived.toJson());
                        System.out.println("Message Received: " + MsgReceived.toJson());
                        processCommand(MsgReceived, br, bw);
                    }
                } catch (IOException e) {
                    System.out.println("chucuo");
                    e.printStackTrace();
                    Peer.connectingPeers.values().remove(socket);
                    Peer.incomingPeers.values().remove(socket);
                    Thread.sleep(500);
                    bw.close();
                    br.close();
                    socket.close();
                    connecting = false;
                }
            }
            bw.close();
            br.close();
            socket.close();
            Peer.connectingPeers.values().remove(socket);
            Peer.incomingPeers.values().remove(socket);
            Thread.sleep(500);
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }
    }

    /**
     * Takes a message object and do operations according the protocol and API
     */
    public void processCommand(Document MsgReceived, BufferedReader br, BufferedWriter bw)  {

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
        try{
            switch (command) {

                case "INVALID_PROTOCOL": {
                    //close the connection
                    Peer.connectingPeers.values().remove(socket);
                    Peer.incomingPeers.values().remove(socket);
                    connecting = false;
                    break;
                }
                case "CONNECTION_REFUSED": {
                    //close the connection
                    connecting = false;
                    //do a breadth first search of peers in the peers list, attempt to make a connection to one of them.
                    Peer.TcpBFS((ArrayList<Document>) MsgReceived.get("peers"), sm);
                    break;
                }
                case "HANDSHAKE_REQUEST": {
                    synchronized (Peer.connectingPeers) {
                        //get the foreign host and port from handshake message
                        Document hostPort = (Document) MsgReceived.get("hostPort");
                        int port = (int) hostPort.getLong("port");
                        String host = hostPort.getString("host");
                        //check if connected to the peer already
                        if (Peer.connectingPeers.keySet().contains(host + ":" + port)) {
                            response = responseGenerator.DuplicationRefuse();
                            bw.write(response.toJson());
                            bw.newLine();
                            bw.flush();
                            connecting = false;
                        }
                        //Check if maximum number reaches
                        else if (Peer.incomingPeers.size() >= Integer.parseInt(Configuration.getConfigurationValue("maximumIncommingConnections"))) {
                            //send refuse connection
                            response = responseGenerator.TcpConnectingMax(Peer.connectingPeers);
                            bw.write(response.toJson());
                            bw.newLine();
                            bw.flush();
                            connecting = false;
                        } else {
                            //send handshake response
                            Peer.connectingPeers.put(host + ":" + port, socket);
                            //Also add it into the incoming connection peers
                            Peer.incomingPeers.put(host + ":" + port, socket);
                            response = responseGenerator.HandshakeResponse();
                            bw.write(response.toJson());
                            bw.newLine();
                            bw.flush();
                            try {
                                Thread.sleep(600);
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }
                            log.info("Connected Peers are：" + Peer.connectingPeers);
                            //initial synchronization
                            sm.synchronize(sm.fileSystemManager.generateSyncEvents(), socket);

                        }
                    }
                    break;
                }
                case "HANDSHAKE_RESPONSE": {
                    //get the foreign host and port from handshake message
                    Document hostPort = (Document) MsgReceived.get("hostPort");
                    int port = (int) hostPort.getLong("port");
                    String host = hostPort.getString("host");
                    //Add it into existing connected peers
                    Peer.connectingPeers.put(host + ":" + port, socket);
                    log.info("Connected Peers are：" + Peer.connectingPeers);
                    try {
                        Thread.sleep(600);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    //Initialize synchronization
                    sm.synchronize(sm.fileSystemManager.generateSyncEvents(), socket);
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
                    bw.write(response.toJson());
                    bw.newLine();
                    bw.flush();

                    //sending filebytes request
                    if (status) {
                        //make a local copy
                        if (sm.fileSystemManager.checkShortcut(pathName)) {
//                        System.out.println("local copy can be used");
                            break;
                        }
                        long position = 0;
                        long length = Math.min(blockSize, fileDescriptor.getLong("fileSize"));
                        response = responseGenerator.FileBytesRequest(pathName, fileDescriptor, position, length);
                        bw.write(response.toJson());
                        bw.newLine();
                        bw.flush();

                        //if files have different content with same pathname
                    } else if (pathSafe && !fileSame) {
                        if (sm.fileSystemManager.modifyFileLoader(pathName, md5, lastModified)) {
                            long position = 0;
                            long length = Math.min(blockSize, fileDescriptor.getLong("fileSize"));
                            response = responseGenerator.FileBytesRequest(pathName, fileDescriptor, position, length);
                            bw.write(response.toJson());
                            bw.newLine();
                            bw.flush();
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
                    bw.write(response.toJson());
                    bw.newLine();
                    bw.flush();
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
                    bw.write(response.toJson());
                    bw.newLine();
                    bw.flush();

                    if (status) {
                        //
                        long position = 0;
                        long length = Math.min(blockSize, fileDescriptor.getLong("fileSize"));
                        response = responseGenerator.FileBytesRequest(pathName, fileDescriptor, position, length);
                        bw.write(response.toJson());
                        bw.newLine();
                        bw.flush();
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
                    bw.write(response.toJson());
                    bw.newLine();
                    bw.flush();
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
                    bw.write(response.toJson());
                    bw.newLine();
                    bw.flush();
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
                    bw.write(response.toJson());
                    bw.newLine();
                    bw.flush();
                    break;
                }
                case "FILE_BYTES_RESPONSE": {
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
                                bw.write(response.toJson());
                                bw.newLine();
                                bw.flush();
                            } else if (sm.fileSystemManager.checkWriteComplete(pathName)) {
                                //whole file fully written
                                sm.fileSystemManager.cancelFileLoader(pathName);
                            } else {
                                System.out.println("file write failed");
                            }
                        }
                    }
                    break;
                }
                default:
                    //no matched command field, which means it is a invalid protocol
                    response = responseGenerator.InvalidProtocol();
                    bw.write(response.toJson());
                    bw.newLine();
                    bw.flush();
            }
        } catch (IOException | NoSuchAlgorithmException e) {
            e.printStackTrace();
        }
    }
}