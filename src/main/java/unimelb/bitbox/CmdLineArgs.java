package unimelb.bitbox;

//Remember to add the args4j jar to your project's build path
import org.kohsuke.args4j.Option;

//This class is where the arguments read from the command line will be stored
//Declare one field for each argument and use the @Option annotation to link the field
//to the argument name, args4J will parse the arguments and based on the name,
//it will automatically update the field with the parsed argument value
public class CmdLineArgs {

	//the command used to manipulate peers
	@Option(required = true, name = "-c", usage = "command")
	private String command;


	//the hostport of the server
	@Option(required = true, name = "-s", usage = "server")
	private String server;


	//the hostport of peer that the server will connect/disconnect
	@Option(required = false, name = "-p", usage = "peer")
	private String peer;

	//the identity of client
	@Option(required = true, name = "-i", usage = "identity")
	private String identity;

	public String getCommand(){
		return command;
	}

	public String getServer(){
		return server;
	}

	public String getPeer(){
		return peer;
	}

	public String getIdentity(){
		return identity;
	}


}
