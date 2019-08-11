package unimelb.bitbox;

import unimelb.bitbox.util.Configuration;
import unimelb.bitbox.util.Document;
import unimelb.bitbox.util.FileSystemManager;
import unimelb.bitbox.util.FileSystemManager.FileSystemEvent;
import unimelb.bitbox.util.FileSystemObserver;
import unimelb.bitbox.MessageGenerator;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.net.*;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.logging.Logger;

public class ServerMain implements FileSystemObserver {
	private static Logger log = Logger.getLogger(ServerMain.class.getName());
	protected FileSystemManager fileSystemManager;


	public ServerMain() throws NumberFormatException, IOException, NoSuchAlgorithmException {
		fileSystemManager=new FileSystemManager(Configuration.getConfigurationValue("path"),this);
	}

	/**
	 * for each fileSystemEvent generated, the peer sends it out to its connecting peers.
	 * @param fileSystemEvent
	 */
	@Override
	public void processFileSystemEvent(FileSystemEvent fileSystemEvent) {
		// TODO: process events
		MessageGenerator MG = new MessageGenerator(fileSystemEvent);
//        log.info("Sending Messages:"+MG.msg.toJson());
		System.out.println("Sending Messages: "+MG.msg.toJson());
		if (Peer.MODE.equals("tcp")) {
			for (Socket socket : Peer.connectingPeers.values()) {
				try {
					BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));
					bw.write(MG.msg.toJson());
					bw.newLine();
					bw.flush();
				} catch (IOException e) {
					log.info(e.getMessage());
				}
			}
		} else if (Peer.MODE.equals("udp")) {
			try {
				String host;
				InetAddress address;
				int port = 0;
				for (String hostPort : Peer.UdpPeers.keySet()) {
					host = hostPort.split(":")[0];
					address = InetAddress.getByName(host);
					port = Integer.parseInt(hostPort.split(":")[1]);

					DatagramSocket sk = new DatagramSocket();

					UdpProcessor test = new UdpProcessor(sk, Peer.sm);
					Thread tt = new Thread(test);
					tt.start();

					Thread.sleep(100);
					UdpProcessor.writeUdp(MG.msg.toJson(), address, port, sk);
					new Thread(new TimeHelper(test, sk, address, port, MG.msg.toJson())).start();

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
	}

	/**
	 * This method is used for initial synchronization as well as periodic synchronizations, it will send out all
	 * fileSystemEvent generated
	 * @param pathEvent
	 * @param socket
	 */
	public void synchronize(ArrayList<FileSystemEvent> pathEvent, Socket socket){
		for(FileSystemEvent event:pathEvent){
			MessageGenerator MG = new MessageGenerator(event);
			try{
				log.info("Synchronizing...");
				BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));

				bw.write(MG.msg.toJson());
				bw.newLine();
				bw.flush();
			} catch (IOException e){
				log.warning(e.getMessage());
				Peer.connectingPeers.values().remove(socket);
			}
		}
	}

	public void synchronize(ArrayList<FileSystemEvent> pathEvent, String host, int port){
		for(FileSystemEvent event:pathEvent){
			MessageGenerator MG = new MessageGenerator(event);
			log.info("Synchronizing...");
			try {
				DatagramSocket sk = new DatagramSocket();
				InetAddress address = InetAddress.getByName(host);

				UdpProcessor test = new UdpProcessor(sk, Peer.sm);
				Thread tt = new Thread(test);
				tt.start();
				Thread.sleep(100);
				UdpProcessor.writeUdp(MG.msg.toJson(), address, port, sk);

				new Thread(new TimeHelper(test, sk, address, port, MG.msg.toJson())).start();
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}

}