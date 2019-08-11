package unimelb.bitbox;

import unimelb.bitbox.util.Document;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.io.*;
import java.net.Socket;
import java.util.Base64;

public class ClientProcessor implements Runnable {
    private String cmd;
    private String serverAdd;
    private String peerAdd;
    private SecretKey sk;
    private boolean connecting = true;

    public ClientProcessor (String command, String s) {
        this.cmd = command;
        this.serverAdd = s;
    }

    public ClientProcessor (String command, String s, String p) {
        this.cmd = command;
        this.serverAdd = s;
        this.peerAdd = p;
    }

    public void run () {
        String serverHost = serverAdd.split(":")[0];
        int serverPort = Integer.parseInt(serverAdd.split(":")[1]);
        try {
            Socket socket = new Socket(serverHost, serverPort);
            BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), "UTF-8"));
            BufferedReader br = new BufferedReader(new InputStreamReader(socket.getInputStream(), "UTF-8"));
            writeTcp(bw, ClientMessage.openConnection(Client.identity).toJson());

            while (connecting) {
                String reader = br.readLine();
                if (reader != null) {
                    System.out.println("received： " + reader);
                    Document MsgReceived = Document.parse(reader);
                    if (MsgReceived.getString("command") != null) {
                        if (MsgReceived.getString("status").equals("true")) {
                            String Base64PubAes = MsgReceived.getString("AES128");
                            byte[] decodedKey = Base64.getDecoder().decode(Base64PubAes);
                            byte[] decryptedKey = GetKeyPair.decryptByPri(Client.privateKey, decodedKey);
                            if (decryptedKey != null) {
                                sk = new SecretKeySpec(decryptedKey, 0, decryptedKey.length, "AES");
                            }
                        } else {
                            bw.close();
                            br.close();
                            socket.close();
                        }
                    } else {
                        String receivedMsg = AES.decryptbyAES(sk, MsgReceived.getString("payload"));
                        Document Msg = Document.parse(receivedMsg);
                        System.out.println(Msg.toJson());
                        bw.close();
                        br.close();
                        socket.close();
                        connecting = false;
                        throw new InterruptedException("close");
                    }
                }

                switch (cmd) {
                    case "list_peers": {
                        System.out.println(sk);
                        String output = ClientMessage.payloadMessage(ClientMessage.listRequest(), sk).toJson();
                        writeTcp(bw, output);
                        break;
                    }
                    case "connect_peer": {
                        String peerHost = peerAdd.split(":")[0];
                        int peerPort = Integer.parseInt(peerAdd.split(":")[1]);
                        String output = ClientMessage.payloadMessage(ClientMessage.connectPeerRequest(peerHost, peerPort), sk).toJson();
                        writeTcp(bw, output);
                        break;
                    }
                    case "disconnect_peer": {
                        String peerHost = peerAdd.split(":")[0];
                        int peerPort = Integer.parseInt(peerAdd.split(":")[1]);
                        String output = ClientMessage.payloadMessage(ClientMessage.disconnectPeerRequest(peerHost, peerPort), sk).toJson();
                        writeTcp(bw, output);
                        break;
                    }
                }
            }

        } catch (IOException | InterruptedException e) {
            System.out.println("Connection closed");
            
        }
    }

    public static void writeTcp (BufferedWriter bw, String content) {
        try {
            bw.write(content);
            bw.newLine();
            bw.flush();
        } catch (IOException e) {
            System.out.println("Connection closed");
        }

    }
}
