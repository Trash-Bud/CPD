package example.cpd;


import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.KeyStore.Entry;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;




//responsible for cluster membership initialization
public class Membership {
    private String       membershipLogFileName;
    private Map<String, String[]> membershipClusterCounter = new HashMap<>();
    // hashedNodeId -> regularNodeId
    private TreeMap<String, String> activeNodesCluster = new TreeMap<>(new SortbyHex());
    private int membershipCounter;
    private String onJoinTimeStamp;

    private boolean isClusterLeader = false;
    private int          storePort ;
    private String         storeAddress;

    private String multicastAddress;
    private int multicastPort;

    private String folderPath;
    Membership(String membershipLogFilename, int serverPort, String serverAddress, int multicastPort, String multicastAddress, String folder_path){
        this.membershipLogFileName = membershipLogFilename;
        this.membershipCounter = -1;
        this.storePort = serverPort;

        this.folderPath = folder_path;
        this.storeAddress = serverAddress;
        this.multicastPort = multicastPort;
        this.multicastAddress = multicastAddress;
      
    }

    public String getFolderPath(){
      return this.folderPath;
    }

    public List<String> getTwoActiveNodeIds(){
        List<String> returnList = new ArrayList<>();
        var activeNodeIds = activeNodesCluster.values();
        activeNodeIds.remove(getOwnNodeId());
        
        int counter = 0;
        for(String nodeId : activeNodeIds){
          if(counter < 2){
            returnList.add(nodeId);
            counter++;
          }else{
            break;
          }
        }
        return returnList;
    }

    public String getOwnSucessor(){
      if(!activeNodesCluster.values().contains(getOwnNodeId())){
          this.activeNodesCluster.put(generateHash(getOwnNodeId()), getOwnNodeId());
      }
      for (Map.Entry<String, String> entry : activeNodesCluster.entrySet()) {
        if(entry.getValue().equals(getOwnNodeId())){
          Map.Entry<String, String> next = activeNodesCluster.higherEntry(entry.getKey()); // next
          if(next == null){
            return activeNodesCluster.get(activeNodesCluster.firstKey());
          }else{
            return next.getValue();
          }
        }
        
      }
    return getOwnNodeId();

    }

    public void onLeaderLeave() throws IOException{

      if(isClusterLeader){
        isClusterLeader = false;
        activeNodesCluster.remove(generateHash(getOwnNodeId()));
        if(activeNodesCluster.isEmpty()) return;
        String secondOldest = oldestActiveNodeInCluster();
        String[] nodeIdParts = secondOldest.split(":");
        String nodeAdd = nodeIdParts[0];
        String nodePort = nodeIdParts[1];
        UDPMulitcastServer multicast_leader_change = new UDPMulitcastServer(this.multicastAddress, this.multicastPort);
        multicast_leader_change.sendMulticastUDPMessage("LEADER CHANGE MESSAGE, NEW LEADER IS STORE ON IP ADDRESS, PORT : " +
        nodeAdd + " " + nodePort
        );
      }
    }

    public void deleteAllActiveNodes(){
      this.activeNodesCluster.clear();
    }

    public String oldestActiveNodeInCluster(){
      Map<Date, String> nodeTimeStamps = new HashMap<>(); 
      for(String nodeId : activeNodesCluster.values()){
        String timestamp = membershipClusterCounter.get(nodeId)[1];
        Date date = null;
        try{
          date = new SimpleDateFormat("yyyy-MM-dd,HH:mm:ss").parse(timestamp); 
        }catch(ParseException exception){
          exception.printStackTrace();
        }
        
        nodeTimeStamps.put(date, nodeId);
      }
      Date earliestJoinStamp = Collections.min(nodeTimeStamps.keySet());
      System.out.println("earlist time stamp: " + earliestJoinStamp);
      System.out.println(nodeTimeStamps.get(earliestJoinStamp));
      return nodeTimeStamps.get(earliestJoinStamp);
      
    }

    public String getOwnMulticastAddress(){
      return this.multicastAddress;
    }
    public int getOwnMulticastPort(){
      return this.multicastPort;
    }
    public String getMembershipLogFileName(){
      return this.membershipLogFileName;
    }

    public TreeMap<String, String> getActiveNodeIds(){
      return activeNodesCluster;
    }

    public void setClusterLeader(boolean isLeader){
      isClusterLeader = isLeader;
    }
    public boolean isClusterLeader(){
      return isClusterLeader;
    }
    public void addActiveNode(String hashedNodeId, String nodeId){
      activeNodesCluster.put(hashedNodeId, nodeId);
    }
    public int getOwnStorePort(){
      return this.storePort;
    }
    public String getOwnStoreAddress(){
      return this.storeAddress;
    }
    public String getOwnNodeId(){
      return this.storeAddress + ":" + this.storePort;
    }
    public static String generateHash(String toHash) {
      MessageDigest digest = null;
      try{
        digest = MessageDigest.getInstance("SHA-256");
      }catch(NoSuchAlgorithmException e){
        e.printStackTrace();

      }
      
      byte[] encodedhash = digest.digest(
          toHash.getBytes(StandardCharsets.UTF_8));

      StringBuilder hexString = new StringBuilder(2 * encodedhash.length);
      for (int i = 0; i < encodedhash.length; i++) {
          String hex = Integer.toHexString(0xff & encodedhash[i]);
          if(hex.length() == 1) {
              hexString.append('0');
          }
          hexString.append(hex);
      }
      return hexString.toString();
    }

    class SortbyHex implements Comparator<String> {
     
      public int compare(String a, String b)
      {
        return a.compareTo(b);
      }
    }

   //returns node_address:port
    public String getResponsibleNode(String keyHash){
      if(!activeNodesCluster.values().contains(getOwnNodeId())){
        this.activeNodesCluster.put(generateHash(getOwnNodeId()), getOwnNodeId());
      }
    
      List<String> nodeHashes =  new ArrayList<>(activeNodesCluster.keySet());
      nodeHashes.sort(new SortbyHex());
    
      int l = 0, r = nodeHashes.size() - 1;
      while (l < r) {
            int m = l + (r - l) / 2;
 
            // Check if x is present at mid
            if (nodeHashes.get(m).equals(keyHash)){
                l = m;
                break;
            }
                
 
            // If x greater, ignore left half
            if (nodeHashes.get(m).compareTo(keyHash) < 0)
                l = m + 1;
 
            // If x is smaller, ignore right half
            else
                r = m;
      }
        
      
      return activeNodesCluster.get(nodeHashes.get(l));
    
    }

    public void updateLog(){
       //write to LOG
        try{
            FileWriter myWriter = new FileWriter(this.membershipLogFileName);
            for(String key: membershipClusterCounter.keySet()){
                String node_id = key;
                String timestamp = membershipClusterCounter.get(key)[1];
                String membershipCounter =  membershipClusterCounter.get(key)[0];
                myWriter.write(timestamp + " " + node_id + " " + membershipCounter + "\n");
            }
            myWriter.close();
        }catch(IOException e){
            e.printStackTrace();
        }
    }

    public void addNewNodeToCluster(String node_id, String[] info){
      
      membershipClusterCounter.put(node_id, info);
      String hashedNodeId = generateHash(node_id);
      if( Integer.parseInt(info[0]) % 2 != 0 ){ //node is leaving :(
        activeNodesCluster.remove(hashedNodeId);
        return;
      }
      if(activeNodesCluster.get(hashedNodeId) == null)
        activeNodesCluster.put(hashedNodeId, node_id);
      
      System.out.println(activeNodesCluster.toString());
      
    }


    Map<String, String[]> getmembershipClusterCounter(){
        return membershipClusterCounter;
    }
    String getJoinTimeStamp(){
      return onJoinTimeStamp;
    }
    public int getMembershipCounter(){
      return membershipCounter;
    }
    public void incrementMembershipCounter(){
      this.membershipCounter = this.membershipCounter + 1;
    }
    public void createMembershipLog(){
        //create membership log
        try {
            File membershipLog = new File(this.membershipLogFileName);
            if (membershipLog.createNewFile()) {
              System.out.println("File created: " + membershipLog.getName());
                onJoinTimeStamp = new SimpleDateFormat("yyyy-MM-dd,HH:mm:ss").format(new java.util.Date());
                addNewNodeToCluster(this.storeAddress + ":" + String.valueOf(this.storePort),new String[]{ String.valueOf(0), onJoinTimeStamp});
                updateLog();
            } else {
              System.out.println("File already exists.");
              //get membership counter 
              try  
              {  
                                                               //creates a new file instance  
                FileReader fr= new FileReader(membershipLog);   //reads the file  
                BufferedReader br= new BufferedReader(fr);     //creates a buffering character input stream  
              
                String line;  
                while((line=br.readLine())!=null)  
                {  
                  var parts = line.split(" ");
                  addNewNodeToCluster(parts[1],new String[]{ parts[2], parts[0]});
                  
                }  
                fr.close();    //closes the stream and release the resources  
    
                this.membershipCounter = Integer.parseInt(this.membershipClusterCounter.get(this.storeAddress+":"+this.storePort)[0]);


              }  
              catch(IOException e)  
              {  
                e.printStackTrace();  
              }  

            }
          } catch (IOException e) {
            System.out.println("An error occurred.");
            e.printStackTrace();
          }

          
    }

}
