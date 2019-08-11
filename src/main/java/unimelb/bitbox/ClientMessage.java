package unimelb.bitbox;

import sun.print.DocumentPropertiesUI;
import unimelb.bitbox.util.Document;
import unimelb.bitbox.util.HostPort;

import javax.crypto.SecretKey;
import javax.print.Doc;
import java.net.DatagramSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashMap;

public class ClientMessage {

    public static Document openConnection (String identity) {
        /**
         * create by: Cyril
         * description: Generate a "AUTH_REQUEST" message to ask for a connection to a bitbox peer
         * create time: 2019/5/21 15:09
         *
         * @Param: identity
         * @return unimelb.bitbox.util.Document
         */
        Document output = new Document();
        output.append("command", "AUTH_REQUEST");
        output.append("identity", identity);
        return output;
    }

    public static Document successResponse (String Base64encryptKey) {
        /**
         * create by: Cyril
         * description: Generate a "AUTH_RESPONSE" message when id is authorized
         * create time: 2019/5/21 15:10
         *
         * @Param: Base64encryptKey, an AES secretKey encrypted by public key and encoded by Base64
         * @return unimelb.bitbox.util.Document
         */
        Document output = new Document();
        output.append("command", "AUTH_RESPONSE");
        output.append("AES128", Base64encryptKey);
        output.append("status", "true");
        output.append("message", "public key found");
        return output;
    }

    public static Document failResponse () {
        /**
         * create by: Cyril
         * description: Generate a "AUTH_RESPONSE" message when id is unauthorized
         * create time: 2019/5/21 15:11
         *
         * @Param:
         * @return unimelb.bitbox.util.Document
         */
        Document output = new Document();
        output.append("command", "AUTH_RESPONSE");
        output.append("status", "false");
        output.append("message", "public key not found");
        return output;
    }

    public static Document listRequest () {
        /**
         * create by: Cyril
         * description: Generate a "LIST_PEERS_REQUEST" message to ask for a list of available peers
         * create time: 2019/5/21 15:11
         *
         * @Param:
         * @return unimelb.bitbox.util.Document
         */
        Document output = new Document();
        output.append("command", "LIST_PEERS_REQUEST");
        return output;
    }

    public static Document listTCPResponse (HashMap<String, Socket> incomingPeers) {
        /**
         * create by: Cyril
         * description: Generate a "LIST_PEERS_RESPONSE" message to show a list of available peers
         * create time: 2019/5/21 15:12
         *
         * @Param: peers
         * @return unimelb.bitbox.util.Document
         */
        Document output = new Document();
        ArrayList<Document> peers = new ArrayList<>();
        //create a list of existing incoming connections
        for (String hostPort : incomingPeers.keySet()) {
            Document peer = new Document();
            peer.append("host", new HostPort(hostPort).host);
            peer.append("port", new HostPort(hostPort).port);
            peers.add(peer);
        }
        output.append("command", "LIST_PEERS_RESPONSE");
        output.append("peers", peers);
        return output;
    }

    public static Document listUDPResponse (HashMap<String, String> incomingPeers) {
        /**
         * create by: Cyril
         * description: Generate a "LIST_PEERS_RESPONSE" message to show a list of available peers
         * create time: 2019/5/21 15:12
         *
         * @Param: peers
         * @return unimelb.bitbox.util.Document
         */
        Document output = new Document();
        ArrayList<Document> peers = new ArrayList<>();
        //create a list of existing incoming connections
        for (String hostPort : incomingPeers.keySet()) {
            Document peer = new Document();
            peer.append("host", new HostPort(hostPort).host);
            peer.append("port", new HostPort(hostPort).port);
            peers.add(peer);
        }
        output.append("command", "LIST_PEERS_RESPONSE");
        output.append("peers", peers);
        return output;
    }

    public static Document connectPeerRequest (String host, int port) {
        /**
         * create by: Cyril
         * description: Generate a "CONNECT_PEER_REQUEST" message to make the peer connect to the given peer
         * create time: 2019/5/21 15:13
         *
         * @Param: hostPort, the address for the peer to connect
         * @return unimelb.bitbox.util.Document
         */
        Document output = new Document();
        output.append("command", "CONNECT_PEER_REQUEST");
        output.append("host", host);
        output.append("port", port);
        return output;
    }

    public static Document connectPeerResponse (String host, int port, boolean status) {
        /**
         * create by: Cyril
         * description: Generate a "CONNECT_PEER_RESPONSE" message to show whether succeed connected to the peer
         * create time: 2019/5/21 15:14
         *
         * @Param: hostPort, the address of the peer required to connect
        * @Param: status, succeed or not
         * @return unimelb.bitbox.util.Document
         */
        Document output = new Document();
        output.append("command", "CONNECT_PEER_RESPONSE");
        output.append("host", host);
        output.append("port", port);
        output.append("status", status);
        if (status) output.append("message", "connected to peer");
        else output.append("message", "connection failed");
        return output;
    }

    public static Document disconnectPeerRequest (String host, int port) {
        /**
         * create by: Cyril
         * description: Generate a "DISCONNECT_PEER_REQUEST" message to let the peer disconnect to the given peer
         * create time: 2019/5/21 15:16
         *
         * @Param: hostPort, the address of the peer required to disconnect
         * @return unimelb.bitbox.util.Document
         */
        Document output = new Document();
        output.append("command", "DISCONNECT_PEER_REQUEST");
        output.append("host", host);
        output.append("port", port);
        return output;
    }

    public static Document disconnectPeerResponse (String host, int port, boolean status) {
        /**
         * create by: Cyril
         * description: Generate a "DISCONNECT_PEER_RESPONSE" message to show whether succeed disconnected
         * create time: 2019/5/21 15:16
         *
         * @Param: hostPort, the address of the peer required to disconnect
        * @Param: status, succeed or not
         * @return unimelb.bitbox.util.Document
         */
        Document output = new Document();
        output.append("command", "DISCONNECT_PEER_RESPONSE");
        output.append("host", host);
        output.append("port", port);
        output.append("status", status);
        if (status) output.append("message", "disconnected from peer");
        else output.append("message", "connection not active");
        return output;
    }

    public static Document payloadMessage (Document json, SecretKey sk) {
        /**
         * create by: Cyril
         * description: Generate a message with encrypted message
         * create time: 2019/5/21 15:18
         *
         * @Param: json, the original json Document
         * @return unimelb.bitbox.util.Document
         */
        Document output = new Document();
        String plainText = json.toJson();
        String Base64AESjson = AES.Base64encryptAES(sk, plainText);
        output.append("payload", Base64AESjson);
        return output;
    }

}