package unimelb.bitbox;

import unimelb.bitbox.util.Configuration;

import java.net.DatagramPacket;
import java.net.DatagramSocket;

public class ByteResponseTimer implements Runnable{
    private long initCount = 0;
    private DatagramSocket sk;
    private DatagramPacket pk;
    private String con;
    private int resendNum=0;

    public ByteResponseTimer (long ByteInit, DatagramPacket pk, DatagramSocket sk, String re) {
        this.initCount = ByteInit;
        this.pk = pk;
        this.sk = sk;
        this.con = re;
    }

    @Override
    public void run () {
        while(true) {
            try {
                Thread.sleep(Integer.parseInt(Configuration.getConfigurationValue("udpwaittime")));
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            long newCount = UdpProcessor.ByteNum;
            if(newCount > initCount) {
//                System.out.println("Byte received init： " + initCount);
//                System.out.println("Byte received new :：" + newCount);
                break;
            } else {
                //retransmit
                UdpProcessor.write(con, pk, sk);
                resendNum += 1;
                if (resendNum == Integer.parseInt(Configuration.getConfigurationValue("maxresendtime"))) {
                    break;
                }
            }
        }
    }
}
