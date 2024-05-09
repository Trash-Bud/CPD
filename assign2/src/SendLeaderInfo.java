package example.cpd;


import java.io.*;
import java.util.*;
import java.net.*;

public class SendLeaderInfo implements Runnable{
    private Membership membershipInfo;
    private int multicastPort;
    private String multicastAddress;
    private String storeAddress;
    private int storePort;
    private String UDPLeaderInfoPrefix = "LEADER MEMBERSHIP INFO FROM IP ADDRESS, PORT : ";

    SendLeaderInfo(Membership membershipInfo){
        this.membershipInfo = membershipInfo;
        this.multicastPort = membershipInfo.getOwnMulticastPort();
        this.multicastAddress = membershipInfo.getOwnMulticastAddress();
        this.storeAddress = membershipInfo.getOwnStoreAddress();
        this.storePort = membershipInfo.getOwnStorePort();
    }
    @Override
    public void run() {
        UDPMulitcastServer multicast_leader = new UDPMulitcastServer(this.multicastAddress, this.multicastPort);
        try{
            while(membershipInfo.getMembershipCounter() % 2 == 0){ //own membershipCounter is even - its in the cluster
                Thread.sleep(20 * 1000);
                if(membershipInfo.isClusterLeader()){
                    System.out.println("We are the cluster leader - sending out our info now");
                    File file= new File(this.membershipInfo.getMembershipLogFileName());  //creates a new file instance  
                    FileReader fr=new FileReader(file);   //reads the file  
                    
                    BufferedReader br=new BufferedReader(fr);  //creates a buffering character input stream  
                    List<String> mostRecentMembershipLogs = new ArrayList<>();
                    String line;  
                    while((line=br.readLine())!=null)  
                    {  
                        mostRecentMembershipLogs.add(line);      //appends line to string buffer  
                        
                    }  
                    
                    while (mostRecentMembershipLogs.size() > 32){
                        mostRecentMembershipLogs.remove(0);
                    }

                    for(String membershipLine: mostRecentMembershipLogs){
                        multicast_leader.sendMulticastUDPMessage(UDPLeaderInfoPrefix +  
                            this.storeAddress + " " + 
                            this.storePort +  " " + membershipLine);
                    }
                }
                

            }
            
        }catch(IOException e){
            e.printStackTrace();
        }catch(InterruptedException e){
            e.printStackTrace();
        }
        
    }

}
