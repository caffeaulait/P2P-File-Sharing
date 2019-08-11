package unimelb.bitbox;

import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import unimelb.bitbox.util.Configuration;

import javax.crypto.SecretKey;
import java.security.interfaces.RSAPrivateKey;

public class Client {
    protected static SecretKey sk;
    public static RSAPrivateKey privateKey;
    public static int port = Integer.parseInt(Configuration.getConfigurationValue("clientPort"));
    public static String identity;
    public static String command;
    public static String server;
    public static String peer;
    public static void main(String[] args){


        try {
            privateKey = GetKeyPair.getPrifromFile("id_rsa");
        } catch (Exception e) {
            System.out.println("Failed get privateKey");
        }


        //Object that will store the parsed command line arguments
        CmdLineArgs argsBean = new CmdLineArgs();

        //Parser provided by args4j
        CmdLineParser parser = new CmdLineParser(argsBean);
        try {

            //Parse the arguments
            parser.parseArgument(args);

            //After parsing, the fields in argsBean have been updated with the given
            //command line arguments
            command = argsBean.getCommand();
            server = argsBean.getServer();
            peer =  argsBean.getPeer();
            identity = argsBean.getIdentity();
            System.out.println(command);

            if (peer!=null) {
                Peer.threadPool.execute(new ClientProcessor(command, server, peer));
            } else {
                Peer.threadPool.execute(new ClientProcessor(command, server));
            }
        } catch (CmdLineException e) {

            System.err.println(e.getMessage());

            //Print the usage to help the user understand the arguments expected
            //by the program
            parser.printUsage(System.err);
        } catch (Exception e) {
            System.out.println("Failed");
        }

    }

}






