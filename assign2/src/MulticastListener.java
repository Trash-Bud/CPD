package example.cpd;


import java.io.*;
import java.net.*;
import java.util.*;

import javax.sound.midi.Soundbank;

import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.SimpleDateFormat;


public class MulticastListener implements Runnable{

    private String multicastAddress;
    private int multicastPort;
    private String storeAddress;
    private int storePort;
    protected boolean isStopped = false;
    private Membership membershipInfo;
    private String UDPJoinMessagePrefix = "JOIN REQUEST FROM STORE on address, port,  membership counter,accepting TCP connections on : ";
    private String UDPLeaveMessagePrefix = "LEAVE REQUEST FROM STORE on address, port,  membership counter,accepting TCP connections on : ";
    private String UDPLeaderInfoPrefix = "LEADER MEMBERSHIP INFO FROM IP ADDRESS, PORT : ";
    private String UDPDeletePrefix = "DELETE WITH HASH_VALUE : ";
    private String UDPLeaderChangePrefix = "LEADER CHANGE MESSAGE, NEW LEADER IS STORE ON IP ADDRESS, PORT : ";

    private List<TCPSendMembershipInfo> tcpSendMembershipInfos = new ArrayList<>();

    MulticastSocket socket= null; //UDP messages with cluster
    InetAddress group= null;

    private int storeTCPmembershipPort;


    private String membershipLogFileName;

    public MulticastListener(String multicastAdress, int multicastPort, String storeAddress, 
    int storePort, int storeTCPmembershipPort, String membershipLogFileName, Membership membershipInfo) {
        this.multicastAddress = multicastAdress;
        this.multicastPort = multicastPort;
        this.storeAddress = storeAddress;
        this.storePort = storePort;
      
        this.membershipInfo = membershipInfo;
        this.storeTCPmembershipPort = storeTCPmembershipPort;
        this.membershipLogFileName = membershipLogFileName;

        System.out.println("starting a multicast worker to handle a new join request!");
    }


    public void writeToLog(String msgWithoutPrefix) throws IOException{
        var msgParts = msgWithoutPrefix.split(" ");
        String timeStamp = new SimpleDateFormat("yyyy-MM-dd,HH:mm:ss").format(new java.util.Date());
        
        membershipInfo.addNewNodeToCluster(msgParts[0] + ":" + msgParts[1], new String[] {msgParts[2], timeStamp});
        membershipInfo.updateLog();
        
    }

    public void parseUDPLeaveMessage(String msgReceived) throws IOException{
        String msgWithoutPrefix = msgReceived.substring(UDPLeaveMessagePrefix.length());
        var msgParts = msgWithoutPrefix.split(" ");
        writeToLog(msgWithoutPrefix);

        int leavePort = 0;
        String leaveAddress = msgParts[0];
        try{
          leavePort = Integer.parseInt(msgParts[1]);
        }
        catch(NumberFormatException e){
          e.printStackTrace();
        }
        
        
    }
    public void parseUDPLeaderMessage(String msgReceived) throws IOException{
      String msgWithoutPrefix = msgReceived.substring(UDPLeaderInfoPrefix.length());
      var msgParts = msgWithoutPrefix.split(" ");
      String TCPmembershipAddress = msgParts[0];
      String membershipLine = msgParts[2];


      String line = msgParts[2] + " " + msgParts[3] + " " + msgParts[4];
      // [<timestamp>, 127.0.0.1:3000, <membership_counter>]
      String node_id = msgParts[3];
      int node_membership_counter = Integer.parseInt(msgParts[4]);
      if(membershipInfo.getOwnNodeId().equals(node_id)){
        
        return;
      }

      Map<String, String[]> memberShipMapCluster = membershipInfo.getmembershipClusterCounter();
      membershipInfo.setClusterLeader(false);
      if(memberShipMapCluster.get(node_id) == null || Integer.parseInt(memberShipMapCluster.get(node_id)[0]) < node_membership_counter){
          membershipInfo.addNewNodeToCluster(node_id, new String[] {String.valueOf(node_membership_counter), msgParts[2]});
          writeToLog(line);
          
          
      }else if(Integer.parseInt(memberShipMapCluster.get(node_id)[0]) > node_membership_counter ){
        //this node has more updated info then the current leader
        System.out.println("[LEADER INFO] Found more updated info in this node, so we are becoming leader");
        membershipInfo.setClusterLeader(true);
        UDPMulitcastServer multicast_leader_change = new UDPMulitcastServer(this.multicastAddress, this.multicastPort);
        multicast_leader_change.sendMulticastUDPMessage(UDPLeaderChangePrefix + this.storeAddress + " " + this.storePort);
      }

      
    }
    public void parseUDPLeaderChangeMessage(String msgReceived ) throws IOException{
        String msgWithoutPrefix = msgReceived.substring(UDPLeaderChangePrefix.length());
        var msgParts = msgWithoutPrefix.split(" ");
        String newLeaderAddress = msgParts[0];
        String newLeaderPort = msgParts[1];

        if(newLeaderAddress.equals(this.storeAddress) && newLeaderPort.equals(String.valueOf(this.storePort))){
          membershipInfo.setClusterLeader(true);
        }else{
          membershipInfo.setClusterLeader(false);
        }


    }
    public void parseUDPDeleteMessage(String msgReceived ) throws IOException{
      String msgWithoutPrefix = msgReceived.substring(UDPDeletePrefix.length());
      var msgParts = msgWithoutPrefix.split(" ");
      String hashValue = msgParts[0];

      String file_path = membershipInfo.getFolderPath() + "\\" + hashValue + ".txt";
      File hashFile = new File(file_path);
      if(hashFile.exists()){
          hashFile.delete();
          System.out.println("DELETED FILE: " + hashValue + "successfully!");
      }


  }
    public void parseUDPJoinMessage(String msgReceived) throws IOException{
        String msgWithoutPrefix = msgReceived.substring(UDPJoinMessagePrefix.length());
        var msgParts = msgWithoutPrefix.split(" "); 
      
        writeToLog(msgWithoutPrefix);

        int TCPmembershipPort = 0;
        int joiningNodePort = 0;
        String TCPmembershipAddress = msgParts[0];
        try{
          TCPmembershipPort = Integer.parseInt(msgParts[3]);
        }
        catch(NumberFormatException e){
          e.printStackTrace();
        }

        try{
          joiningNodePort = Integer.parseInt(msgParts[1]);
        }
        catch(NumberFormatException e){
          e.printStackTrace();
        }
        
        if(joiningNodePort == this.membershipInfo.getOwnStorePort()
         && TCPmembershipAddress.equals(this.membershipInfo.getOwnStoreAddress())){
          return;
        }

        //check if we are the joining node's sucessor
        if(this.membershipInfo.getOwnSucessor().equals(TCPmembershipAddress + ":" + joiningNodePort)){
          System.out.println("WE ARE THE JOINING NODE'S NEW SUCESSOR, SENDING THEM THEIR RESPECTIVE KEYS");
          TCPKeyTransfer keySender = new TCPKeyTransfer(membershipInfo, false);
          new Thread(keySender).start();
        }else{
          System.out.println("JOINING NODE IS NOT OUR SUCESSOR");
        }
        TCPSendMembershipInfo infoSender = new TCPSendMembershipInfo(TCPmembershipPort, TCPmembershipAddress, membershipInfo);
        if(tcpSendMembershipInfos.contains(infoSender)){
          System.out.println("Already sent out information to this destinatary! ");
        }else{
          tcpSendMembershipInfos.add(infoSender);
          new Thread(infoSender).start();
        }
        
    }

   private synchronized boolean isStopped() {
        return this.isStopped;
    }

    public synchronized void stop() throws IOException{
        this.isStopped = true;
        
          //we are leaving the node, so send a LEAVE message
          
          UDPMulitcastServer multicast_leave = new UDPMulitcastServer(this.multicastAddress, this.multicastPort);
          multicast_leave.sendMulticastUDPMessage(UDPLeaveMessagePrefix + 
          this.storeAddress + " " + this.storePort + " " + this.membershipInfo.getMembershipCounter() );
        try {
            socket.leaveGroup(group);
            socket.close();
        } catch (IOException e) {
            throw new RuntimeException("Error closing server", e);
        }

       
    }
    public void run() {

        //write to his own membership log his entrance
        try{
          UDPMulitcastServer multicast_join = new UDPMulitcastServer(this.multicastAddress, this.multicastPort);
          multicast_join.sendMulticastUDPMessage(UDPJoinMessagePrefix +  
             this.storeAddress + " " + 
            this.storePort + " " + this.membershipInfo.getMembershipCounter() + " " + this.storeTCPmembershipPort );
            

          membershipInfo.addNewNodeToCluster(this.membershipInfo.getOwnNodeId(), 
          new String[] {String.valueOf(this.membershipInfo.getMembershipCounter()), this.membershipInfo.getJoinTimeStamp()});

        byte[] buffer= new byte[1024];

        socket= new MulticastSocket(this.multicastPort); //UDP messages with cluster
        group= InetAddress.getByName(this.multicastAddress);
        socket.joinGroup(group);
        

        //LISTEN FOR JOIN/LEAVE messages
        while(! isStopped()){
            System.out.println("Listening for multicast message...");
            DatagramPacket packet= new DatagramPacket(buffer,
               buffer.length);
            socket.receive(packet);
            String msgReceived = new String(packet.getData(),
            packet.getOffset(),packet.getLength());

            System.out.println("[Multicast UDP message received] >> "+ msgReceived);
            
            if(msgReceived.startsWith(UDPJoinMessagePrefix))
              parseUDPJoinMessage(msgReceived);
            else if(msgReceived.startsWith(UDPLeaveMessagePrefix))
              parseUDPLeaveMessage(msgReceived);
            else if(msgReceived.startsWith(UDPLeaderInfoPrefix))
              parseUDPLeaderMessage(msgReceived);
            else if(msgReceived.startsWith(UDPLeaderChangePrefix))
              parseUDPLeaderChangeMessage(msgReceived);
            else if(msgReceived.startsWith(UDPDeletePrefix))
              parseUDPDeleteMessage(msgReceived);

         }
        }catch(IOException e){
          e.printStackTrace();
        }
        

        
    }
} 
