package example.cpd;

import java.io.*;
import java.util.*;
import java.net.*;


public class TCPGetMembershipInfo implements Runnable{

    private ServerSocket serverSocket = null;
    private int          serverPort   = 8080;
    private boolean      isStopped    = false;
    private String       membershipLogFileName;
    private Membership   membershipInfo;

    TCPGetMembershipInfo(int serverPort, String membershipLogFileName, Membership membershipInfo){
        this.serverPort = serverPort;
        this.membershipLogFileName = membershipLogFileName;
        this.membershipInfo = membershipInfo;

    
    }

    private synchronized boolean isStopped() {
        return this.isStopped;
    }

    public synchronized void stop(){
        this.isStopped = true;
        try {
            this.serverSocket.close();
        } catch (IOException e) {
            throw new RuntimeException("Error closing server", e);
        }
    }

    private void openServerSocket() {
        try {
            this.serverSocket = new ServerSocket(this.serverPort);
        } catch (IOException e) {
            throw new RuntimeException("Cannot open port" + this.serverPort, e);
        }
    }


    private static class EchoClientHandler extends Thread {
        private Socket clientSocket;
        private PrintWriter out;
        private BufferedReader in;
        private String onJoinMembershipTimeStamp;
        private Membership membershipInfo;
        private String       membershipLogFileName;
        public EchoClientHandler(Socket socket, Membership membershipInfo, String membershipLogFileName) {
            this.clientSocket = socket;
            this.membershipInfo = membershipInfo;
            this.onJoinMembershipTimeStamp = membershipInfo.getJoinTimeStamp();
            this.membershipLogFileName = membershipLogFileName;
        }
        
    
        public void run() {
            try{
                
                out = new PrintWriter(clientSocket.getOutputStream(), true);
                in = new BufferedReader(
                new InputStreamReader(clientSocket.getInputStream()));
                
                String storeAddress = "";
                int storePort = 0;
                String[] parts;
                String inputLine;
                Map<String, String[]> memberShipMapCluster = membershipInfo.getmembershipClusterCounter();
                while ((inputLine = in.readLine()) != null) {
                    System.out.println("received TCP info: " + inputLine);
                    if(inputLine.startsWith("START TCP MEMBERSHIP INFO TRANSFER FROM IP_ADRESS, PORT : ")){
                        out.println("ACK");
                    }
                    else if(inputLine.startsWith("START TCP ACTIVE NODES INFO TRANSFER FROM IP_ADRESS, PORT : ")){
                        out.println("ACK ACTIVE NODES");
                    }
                    else if(inputLine.startsWith("ENDED TCP MEMBERSHIP INFO TRANSFER FROM : ")){
                        System.out.println("Finished receiving the 32 most recent log entries");
                    }
                    else if(inputLine.startsWith("ENDED TCP TRANSFER FROM : ")){
                        System.out.println("ended tcp active node membership information FROM: " + storeAddress + ":" + storePort);
                        out.println("bye");
                        break;
                    }
                    else if(inputLine.startsWith("ACTIVE NODE INFO FROM : ")){
                        inputLine = inputLine.substring("ACTIVE NODE INFO FROM : ".length());
                        parts = inputLine.split(" ");
                        storeAddress = parts[0];
                        storePort = Integer.parseInt(parts[1]);
                        String hashedId = parts[2];
                        String nodeId = parts[3];
                        membershipInfo.addActiveNode(hashedId, nodeId);
                    }
                    else{
                    
                        parts = inputLine.split(" ");
                        storeAddress = parts[0];
                        storePort = Integer.parseInt(parts[1]);
                        String line = parts[2] + " " + parts[3] + " " + parts[4];
                        // [<timestamp>, 127.0.0.1:3000, <membership_counter>]
                        String node_id = parts[3];
                        int node_membership_counter = Integer.parseInt(parts[4]);

                        
                        if(memberShipMapCluster.get(node_id) == null || Integer.parseInt(memberShipMapCluster.get(node_id)[0]) < node_membership_counter){
                            membershipInfo.addNewNodeToCluster(node_id, new String[] {String.valueOf(node_membership_counter), parts[2]});
                            
                            
                        }

                    }
                }
                
                System.out.println(memberShipMapCluster.toString());
                System.out.println(membershipInfo.getActiveNodeIds().toString());
                in.close();
                out.flush();
                out.close();

              
                clientSocket.close();
            }catch(IOException e){
                e.printStackTrace();
            }
            
        }
    }

    private static class StopAfterTimeOut extends Thread {
        private TCPGetMembershipInfo tcp;
        private int numOfResends = 0;
        public StopAfterTimeOut(TCPGetMembershipInfo tcp) {
            this.tcp = tcp;
        }
    
        public void run() {
            try{
                while(numOfResends < 2){
                    Thread.sleep(1 * 1000);  //after 10 seconds, give up on receiving TCP membership info from other cluster nodes
                    if(! this.tcp.isStopped()){
                        UDPMulitcastServer multicast_join = new UDPMulitcastServer(tcp.membershipInfo.getOwnMulticastAddress(),
                         tcp.membershipInfo.getOwnMulticastPort());
                         try{
                                multicast_join.sendMulticastUDPMessage("JOIN REQUEST FROM STORE on address, port,  membership counter,accepting TCP connections on : " +  
                            tcp.membershipInfo.getOwnStoreAddress() + " " + 
                            tcp.membershipInfo.getOwnStorePort() + " " + tcp.membershipInfo.getMembershipCounter() + " " + tcp.serverPort );
                         }catch(IOException e){
                             e.printStackTrace();
                         }
                       
                        
                         numOfResends++;
                     }else{
                        break;
                    }
                }

                if(numOfResends == 2){
                    //conclude we are the first node in server and become leader
                    tcp.membershipInfo.setClusterLeader(true);
                }
                System.out.println("Finished trying to obtain TCP membership info");
                tcp.stop();

            }catch(InterruptedException e){
                e.printStackTrace();
            }
            
        
        }
    }
    public void run() {
        openServerSocket();
        System.out.println("open Server for receiving membership info socket on port " + this.serverPort);
        
        int numberOfClients = 0;
        new StopAfterTimeOut(this).start();
        List<Thread> threads = new ArrayList<>();
        while(! isStopped() && numberOfClients < 3){
            try {
                Thread newClientHandler = new EchoClientHandler(serverSocket.accept(), membershipInfo, membershipLogFileName);
                threads.add(newClientHandler);
                newClientHandler.start();
                numberOfClients++;
            } catch (IOException e) {
                if(isStopped()) {
                    System.out.println("Server for listening TCP membership has been Stopped.") ;
                    break;
                }
                throw new RuntimeException(
                    "Error accepting client connection", e);
            }

            
        }
        //wait for all threads to finish
        for(var thread : threads){
            try{
                thread.join();
            }catch(InterruptedException e){
                e.printStackTrace();
            }
            
        }
        
        //we have the updated logs info so check in the active nodes for the one that joined more recently - if its us, become node leader

        if(membershipInfo.oldestActiveNodeInCluster().equals(membershipInfo.getOwnNodeId())){
            System.out.println("IM THE OLDEST NOW");
            membershipInfo.setClusterLeader(true);
        }
        

        //write to LOG

        try{
            FileWriter myWriter = new FileWriter(this.membershipLogFileName);
            Map<String, String[]> memberShipMapCluster = membershipInfo.getmembershipClusterCounter();
            for(String key: memberShipMapCluster.keySet()){
                String node_id = key;
                String timestamp = memberShipMapCluster.get(key)[1];
                String membershipCounter =  memberShipMapCluster.get(key)[0];
                myWriter.write(timestamp + " " + node_id + " " + membershipCounter + "\n");
            }
            myWriter.close();
        }catch(IOException e){
            e.printStackTrace();
        }
        
        System.out.println("Stopped listening for TCP membership info on cluster.") ;
       
    }



}
