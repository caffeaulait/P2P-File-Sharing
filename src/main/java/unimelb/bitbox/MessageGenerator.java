package unimelb.bitbox;

import unimelb.bitbox.util.Configuration;
import unimelb.bitbox.util.Document;
import unimelb.bitbox.util.FileSystemManager;
import unimelb.bitbox.util.HostPort;

import java.net.Socket;
import java.util.ArrayList;
import java.util.HashMap;

/**
 * This class is used to generate Message Objects that are used to communicate between peers.
 */

public class MessageGenerator {
    public Document msg = new Document();

    public MessageGenerator() {
    }

    public MessageGenerator(FileSystemManager.FileSystemEvent fileSystemEvent) {

        switch (fileSystemEvent.event) {
            case FILE_CREATE: {
                msg.append("command", "FILE_CREATE_REQUEST");
                msg.append("fileDescriptor", fileSystemEvent.fileDescriptor.toDoc());
                msg.append("pathName", fileSystemEvent.pathName);
                break;
            }
            case FILE_DELETE: {
                msg.append("command", "FILE_DELETE_REQUEST");
                msg.append("fileDescriptor", fileSystemEvent.fileDescriptor.toDoc());
                msg.append("pathName", fileSystemEvent.pathName);
                break;
            }
            case FILE_MODIFY: {
                msg.append("command", "FILE_MODIFY_REQUEST");
                msg.append("fileDescriptor", fileSystemEvent.fileDescriptor.toDoc());
                msg.append("pathName", fileSystemEvent.pathName);
                break;
            }
            case DIRECTORY_CREATE: {
                msg.append("command", "DIRECTORY_CREATE_REQUEST");
                msg.append("pathName", fileSystemEvent.pathName);
                break;
            }
            case DIRECTORY_DELETE: {
                msg.append("command", "DIRECTORY_DELETE_REQUEST");
                msg.append("pathName", fileSystemEvent.pathName);
                break;
            }
            default:
                msg = null;
        }
    }


    /**
     * @return HANDSHAKE_REQUEST message
     */
    public Document HandshakeRequest( String portMode) {
        Document handshakeRq = new Document();
        HostPort hostPort = new HostPort(Configuration.getConfigurationValue("advertisedName"),
                Integer.parseInt(Configuration.getConfigurationValue(portMode)));
        handshakeRq.append("command", "HANDSHAKE_REQUEST");
        handshakeRq.append("hostPort", hostPort.toDoc());
        return handshakeRq;
    }

    /**
     * @return HANDSHAKE_RESPONSE message
     */
    public Document HandshakeResponse() {
        Document handshakeRsp = new Document();
        HostPort hostPort = new HostPort(Configuration.getConfigurationValue("advertisedName"),
                Integer.parseInt(Configuration.getConfigurationValue("port")));
        handshakeRsp.append("command", "HANDSHAKE_RESPONSE");
        handshakeRsp.append("hostPort", hostPort.toDoc());
        return handshakeRsp;
    }

    /**
     * @return CONNECTION_REFUSED message
     */
    public Document TcpConnectingMax(HashMap<String, Socket> incomingPeers) {
        Document connectionRf = new Document();
        ArrayList<Document> peers = new ArrayList<>();
        //create a list of existing incoming connetions
        for (String hostPort : incomingPeers.keySet()) {
            Document peer = new Document();
            peer.append("host", new HostPort(hostPort).host);
            peer.append("port", new HostPort(hostPort).port);
            peers.add(peer);
        }
        connectionRf.append("command", "CONNECTION_REFUSED");
        connectionRf.append("message", "connection limit reached");
        connectionRf.append("peers", peers);
        return connectionRf;
    }

    public Document UdpAskingMax(HashMap<String, String> incomingPeers) {
        Document connectionRf = new Document();
        ArrayList<Document> peers = new ArrayList<>();
        //create a list of existing incoming connetions
        for (String hostPort : incomingPeers.keySet()) {
            Document peer = new Document();
            peer.append("host", new HostPort(hostPort).host);
            peer.append("port", new HostPort(hostPort).port);
            peers.add(peer);
        }
        connectionRf.append("command", "CONNECTION_REFUSED");
        connectionRf.append("message", "connection limit reached");
        connectionRf.append("peers", peers);
        return connectionRf;
    }

    /**
     * @return INVALID_PROTOCOL message
     */
    public Document DuplicationRefuse() {
        Document duplicateRf = new Document();
        duplicateRf.append("command", "INVALID_PROTOCOL");
        duplicateRf.append("message", "already connected to the peer");
        return duplicateRf;
    }

    /**
     * @return INVALID_PROTOCOL message
     */
        public Document InvalidProtocol() {
            Document invalid = new Document();
            invalid.append("command", "INVALID_PROTOCOL");
            invalid.append("message", "message must contain a valid command");
            return invalid;

        }

    public Document InvalidUdp(String hp) {
        Document invalid = new Document();
        invalid.append("command", "INVALID_PROTOCOL");
        invalid.append("hp", hp);
        return invalid;

    }

    /**
     * @return FILE_CREATE_RESPONSE message
     */
    public Document FileCreateResponse(Document fileCreateRq, Boolean status, Boolean pathsSafe, Boolean fileExist) {
        Document fileCreateRsp = fileCreateRq;
        fileCreateRsp.append("command", "FILE_CREATE_RESPONSE");
        if (status) {
            fileCreateRsp.append("message", "file loader ready");
            fileCreateRsp.append("status", true);
        } else if (!pathsSafe) {
            fileCreateRsp.append("message", "unsafe pathname given");
            fileCreateRsp.append("status", false);
        } else if (fileExist) {
            fileCreateRsp.append("message", "pathname already exists");
            fileCreateRsp.append("status", false);
        } else {
            fileCreateRsp.append("message", "there was a problem creating the file");
            fileCreateRsp.append("status", false);
        }
        return fileCreateRsp;
    }

    /**
     * @return FILE_BYTES_REQUEST message
     */
    public Document FileBytesRequest(String pathname, Document fileDescriptor, long position, long length) {
        Document bytesRq = new Document();
        bytesRq.append("command", "FILE_BYTES_REQUEST");
        bytesRq.append("fileDescriptor", fileDescriptor);
        bytesRq.append("pathName", pathname);
        bytesRq.append("position", position);
        bytesRq.append("length", length);
        return bytesRq;
    }

    /**
     * @return FILE_BYTES_RESPONSE message
     */
    public Document FileBytesResponse(Document bytesRq, String content, Boolean status) {
        Document bytesRsp = bytesRq;
        bytesRsp.append("command", "FILE_BYTES_RESPONSE");
        bytesRsp.append("content", content);
        if (status) {
            bytesRsp.append("message", "successful read");
        } else {
            bytesRsp.append("message", "unsuccessful read");
        }
        bytesRsp.append("status", status);
        return bytesRsp;
    }

    /**
     * @return FILE_DELETE_RESPONSE message
     */
    public Document FileDeleteResponse(Document fileDeleteRq, Boolean status, Boolean pathsSafe, Boolean fileExist) {
        Document fileDeleteRsp = fileDeleteRq;
        fileDeleteRsp.append("command", "FILE_DELETE_RESPONSE");
        if (status) {
            fileDeleteRsp.append("message", "file deleted");
            fileDeleteRsp.append("status", true);
        } else if (!pathsSafe) {
            fileDeleteRsp.append("message", "unsafe pathname given");
            fileDeleteRsp.append("status", false);
        } else if (!fileExist) {
            fileDeleteRsp.append("message", "pathname does not exist");
            fileDeleteRsp.append("status", false);
        } else {
            fileDeleteRsp.append("message", "there was a problem creating the file");
            fileDeleteRsp.append("status", false);
        }
        return fileDeleteRsp;
    }

    /**
     * @return FILE_MODIFY_RESPONSE message
     */
    public Document FileModifyResponse(Document fileModifyRq, Boolean status, Boolean pathsSafe, Boolean pathExist, Boolean fileSame) {
        Document fileModifyRsp = fileModifyRq;
        fileModifyRsp.append("command", "FILE_MODIFY_RESPONSE");
        if (status) {
            fileModifyRsp.append("message", "file loader ready");
            fileModifyRsp.append("status", true);
        } else if (!pathsSafe) {
            fileModifyRsp.append("message", "unsafe pathname given");
            fileModifyRsp.append("status", false);
        } else if (!pathExist) {
            fileModifyRsp.append("message", "pathname does not exist");
            fileModifyRsp.append("status", false);
        } else if (fileSame) {
            fileModifyRsp.append("message", "file already exists with matching content");
            fileModifyRsp.append("status", false);
        } else {
            fileModifyRsp.append("message", "there was a problem creating the file");
            fileModifyRsp.append("status", false);
        }
        return fileModifyRsp;
    }

    /**
     * @return DIRECTORY_CREATE_RESPONSE message
     */
    public Document DirCreateResponse(Document dirCreateRq, Boolean status, Boolean pathsSafe, Boolean dirExist) {
        Document dirCreateRsp = dirCreateRq;
        dirCreateRsp.append("command", "DIRECTORY_CREATE_RESPONSE");
        if (status) {
            dirCreateRsp.append("message", "directory created");
            dirCreateRsp.append("status", true);
        } else if (!pathsSafe) {
            dirCreateRsp.append("message", "unsafe pathname given");
            dirCreateRsp.append("status", false);
        } else if (dirExist) {
            dirCreateRsp.append("message", "pathname already exists");
            dirCreateRsp.append("status", false);
        } else {
            dirCreateRsp.append("message", "there was a problem creating the directory");
            dirCreateRsp.append("status", false);
        }
        return dirCreateRsp;
    }

    /**
     * @return DIRECTORY_DELETE_RESPONSE message
     */
    public Document DirDeleteResponse(Document dirDeleteRq, Boolean status, Boolean pathsSafe, Boolean dirExist) {
        Document dirDeleteRsp = dirDeleteRq;
        dirDeleteRsp.append("command", "DIRECTORY_DELETE_RESPONSE");
        if (status) {
            dirDeleteRsp.append("message", "directory deleted");
            dirDeleteRsp.append("status", true);
        } else if (!pathsSafe) {
            dirDeleteRsp.append("message", "unsafe pathname given");
            dirDeleteRsp.append("status", false);
        } else if (!dirExist) {
            dirDeleteRsp.append("message", "pathname does not exist");
            dirDeleteRsp.append("status", false);
        } else {
            dirDeleteRsp.append("message", "there was a problem deleting the directory");
            dirDeleteRsp.append("status", false);
        }
        return dirDeleteRsp;
    }

}