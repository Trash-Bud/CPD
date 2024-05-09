package example.cpd;

import java.io.*;
import java.net.*;
import java.util.*;

public class TCPSendMembershipInfo implements Runnable{
    private String UDPJoinMessagePrefix = "JOIN REQUEST FROM STORE on address, port,  membership counter,accepting TCP connections on : ";
    private int TCPmembershipPort;
    private String TCPmembershipAddress;

    private Membership membershipInfo;
    TCPSendMembershipInfo(int TCPmembershipPort, String TCPmembershipAddress, Membership membershipInfo){
        this.TCPmembershipPort = TCPmembershipPort;
        this.membershipInfo = membershipInfo;
        this.TCPmembershipAddress = TCPmembershipAddress;
    }
    @Override
    public boolean equals(Object obj) {
        if (this == obj) {    
            return true;    
        }    
        if (obj instanceof TCPSendMembershipInfo) {    
            TCPSendMembershipInfo anotherObj = (TCPSendMembershipInfo) obj;    
            if(anotherObj.TCPmembershipPort == this.TCPmembershipPort && anotherObj.TCPmembershipAddress.equals(this.TCPmembershipAddress)){
                return true;
            } 
        }    
           
        return false;    
    }
    public void run(){
        
          if(TCPmembershipPort != 0){
            try (Socket socket = new Socket(TCPmembershipAddress, TCPmembershipPort)) {
  
              OutputStream output = socket.getOutputStream();
              PrintWriter writer = new PrintWriter(output, true);
              writer.println("START TCP MEMBERSHIP INFO TRANSFER FROM IP_ADRESS, PORT : " + 
              this.membershipInfo.getOwnStoreAddress() + " " + this.membershipInfo.getOwnStorePort());
              
              
              InputStream input = socket.getInputStream();
              BufferedReader reader = new BufferedReader(new InputStreamReader(input));
              String ACK = null;
              while(true){
                  ACK = reader.readLine();
                  if(ACK != null) break;
                
              }
              
              if(ACK == "ACK") System.out.println("ACK SUCCESSFUL, now sending the membership info (32 most recent log entries)");
              
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
                writer.println(this.membershipInfo.getOwnStoreAddress() + " " + 
                this.membershipInfo.getOwnStorePort() + " " + membershipLine);
              }

              writer.println("ENDED TCP MEMBERSHIP INFO TRANSFER FROM : " + this.membershipInfo.getOwnStoreAddress() + " " + 
              this.membershipInfo.getOwnStorePort());
              
              writer.println("START TCP ACTIVE NODES INFO TRANSFER FROM IP_ADRESS, PORT : " + this.membershipInfo.getOwnStoreAddress() + " " + 
              this.membershipInfo.getOwnStorePort());
              
              ACK = null;
              while(true){
                ACK = reader.readLine();
                if(ACK != null) break;
              
            }
            
              if(ACK == "ACK ACTIVE NODES") System.out.println("ACK SUCCESSFUL, now sending the membership info (Active Cluster Nodes)");

              TreeMap<String, String> activeNodeIds = membershipInfo.getActiveNodeIds();

              for(String hashedId : activeNodeIds.keySet()){
                writer.println("ACTIVE NODE INFO FROM : " +this.membershipInfo.getOwnStoreAddress() + " " + 
                this.membershipInfo.getOwnStorePort() + " " + 
                hashedId + " " + activeNodeIds.get(hashedId));
              }


              writer.println("ENDED TCP TRANSFER FROM : " + this.membershipInfo.getOwnStoreAddress() + " " + 
              this.membershipInfo.getOwnStorePort());
              
              writer.close();
              output.flush();
              output.close();
              socket.close();
              
            } catch (UnknownHostException ex) {
  
              System.out.println("Server not found: " + ex.getMessage());
  
            } catch (IOException ex) {
  
              System.out.println("I/O error: " + ex.getMessage());
            }
        }
        
    System.out.println("done MEMBERSHIP message");



   }
}
