package unimelb.bitbox;

import unimelb.bitbox.util.Configuration;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

public class TimeHelper implements Runnable{
    private long iniCount=0;
    private UdpProcessor up;
    private DatagramSocket sk;
    private InetAddress address;
    private int port;
    private String con;
    private int resendNum;

    public TimeHelper (UdpProcessor up, DatagramSocket sk, InetAddress address, int port, String con) {
        this.iniCount = up.receiveCount;
        this.up = up;
        this.sk = sk;
        this.address = address;
        this.port = port;
        this.con = con;
//        System.out.println("Start timing");
    }

    public TimeHelper (long ByteInit, DatagramSocket sk, InetAddress address, int port, String con) {
        this.iniCount = ByteInit;
        this.sk = sk;
        this.address = address;
        this.port = port;
        this.con = con;
//        System.out.println("Start timing");
    }

    @Override
    public void run () {
        while(true) {
            try {
                Thread.sleep(Integer.parseInt(Configuration.getConfigurationValue("udpwaittime")));
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            long newCount = up.receiveCount;
            if(newCount > iniCount) {
//                System.out.println("initCount is: " + iniCount);
//                System.out.println("newCount is： " + newCount);
                break;
            } else {
                //retransimit
                UdpProcessor.writeUdp(con, address, port, sk);
                resendNum += 1;
                if (resendNum == Integer.parseInt(Configuration.getConfigurationValue("maxresendtime"))) {
                    break;
                }
            }
        }
    }
}
