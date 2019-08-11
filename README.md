# Distributed File Sharing System

**There are two connection modes: UDP and TCP**  
To switch between mode, go to the configuration file, and change the mode field


**There are two entry points of the program:**  
One is as Peer, the other is as Client


**Public/Private key pair files are in the Project folder:**  
id_rsa.pub, id_rsa.

## Command Line Arguments:
-i  identity (can be found at the end of public key）  
-s  peer sever that Client will connect  
-c  the command that Client will execute  
-p  Another peer that Client wants its peer server to connect/disconnect  

identity, command ,and server are required fields.  

**Don't forget identity (e.g. ubuntu@couch1)**


## How to run on command line?

CD into the project directory.

**To enter the Peer mode:**

java -cp target/bitbox-0.0.1-SNAPSHOT-jar-with-dependencies.jar unimelb.bitbox.Peer 


**To enter the Client mode, arguments need to be specified:**

(1) to list connecting peers of the Server
java -cp target/bitbox-0.0.1-SNAPSHOT-jar-with-dependencies.jar unimelb.bitbox.Client -c list_peers -s localhost:7001 -i ubuntu@couch1

(2) to ask the Server to connect to a particular Peer
java -cp target/bitbox-0.0.1-SNAPSHOT-jar-with-dependencies.jar unimelb.bitbox.Client -c connect_peer -s localhost:7001 -p localhost:8112 -i ubuntu@couch1

(3) to ask the Server to disconnect from a particular Peer
java -cp target/bitbox-0.0.1-SNAPSHOT-jar-with-dependencies.jar unimelb.bitbox.Client -c disconnect_peer -s localhost:7001 -p localhost:8112 -i ubuntu@couch1
