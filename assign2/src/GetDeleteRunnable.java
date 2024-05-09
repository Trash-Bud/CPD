package example.cpd;

import java.io.*;
import java.net.Socket;
import java.net.UnknownHostException;
import java.nio.file.Files;
import java.nio.file.Path;

/**

 */
public class GetDeleteRunnable implements Runnable{

    protected Socket clientSocket = null;
    protected String testClientText   = null;

    private String folder_path;
    private String IP_address;
    private int storePort;
    private String fileHashValue;
    private String request_prefix;

    private String get_prefix = "GET REQUEST FROM : ";
    private String delete_prefix = "DELETE REQUEST FROM : ";

 
    private Membership membershipInfo;
    

    public GetDeleteRunnable(Socket clientSocket, String clientText, String folder_path, Membership membershipInfo, String request_prefix) {
        this.clientSocket = clientSocket;
        this.testClientText   = clientText;
        this.folder_path = folder_path;
        this.request_prefix = request_prefix;
        this.membershipInfo = membershipInfo;
        System.out.println("starting a worker to handle a new " + request_prefix);

    }

    public void request_node(){
        String request_msg = testClientText.substring(request_prefix.length());
        String[] parts = request_msg.split(" ");
        IP_address = parts[0]; 
        storePort = Integer.parseInt(parts[1].trim());
        fileHashValue = parts[2];
        
    }

    public String redirectRequest(String responsible_IPAddress,int responsible_port)  throws UnknownHostException, IOException{
        Socket skt; //TCP/IP messages with server
    

        skt = new Socket(responsible_IPAddress, responsible_port);
        
        OutputStream output = skt.getOutputStream();
        PrintWriter writer = new PrintWriter(output, true);

        writer.println(request_prefix + membershipInfo.getOwnStoreAddress() 
        + " " + membershipInfo.getOwnStorePort() + " " + fileHashValue); 
        
        InputStream input = skt.getInputStream();
        BufferedReader reader = new BufferedReader(new InputStreamReader(input));
        


        String storeResponse = reader.readLine();
        System.out.println(storeResponse);
        storeResponse = reader.readLine();
        System.out.println(storeResponse);


        output.close();
        skt.close(); 
        return storeResponse;
     
    }

    public void run() {
        try {
            OutputStream output = clientSocket.getOutputStream();
            PrintWriter out = new PrintWriter(output, true);
            
            request_node();
            //send this back to the TestClient so we know that the join request has been acknowledged
            out.println("Received the " + request_prefix  + 
            IP_address + " and port: " + storePort + " and HASH: " + fileHashValue);
            
            InputStream input  = clientSocket.getInputStream();
            BufferedReader reader = new BufferedReader(new InputStreamReader(input));
        
            //check if this node is responsible for this key
            String responsible_node_id = this.membershipInfo.getResponsibleNode(fileHashValue);
            String responsible_IPAddress = responsible_node_id.split(":")[0];
            int responsible_port = Integer.parseInt(responsible_node_id.split(":")[1]);
            String file_path = folder_path + "\\" +fileHashValue + ".txt";
            File hashFile = new File(file_path);
            if(hashFile.exists()){
                if(request_prefix.equals(get_prefix)){
                    Path getFilePath = Path.of(file_path);
                    String fileContent = Files.readString(getFilePath);
                    out.println(fileContent);
                }else if(request_prefix.equals(delete_prefix)){

                     //multicast , so the other replicas are deleted as well
                    UDPMulitcastServer multicast_delete = new UDPMulitcastServer(this.membershipInfo.getOwnMulticastAddress(),
                     this.membershipInfo.getOwnMulticastPort());
                    multicast_delete.sendMulticastUDPMessage("DELETE WITH HASH_VALUE : " + 
                    fileHashValue);
                    hashFile.delete();
                    out.println("DELETED FILE: " + fileHashValue + "successfully!");
                }
               
            }
            else if(responsible_node_id.equals(membershipInfo.getOwnNodeId()) ){
                //we are the responsible node, and the required value should be in our filesystem, however it is not
                
                out.println("The key : " + fileHashValue + " has no value!");
                
            }else{
                //we are not the responsible node, send the info we have via TCP to the responsible man
                System.out.println("We are not the responsible for this GET/DELETE request, redirecting info to the responsible STORE now.");
                try{
                    out.println(redirectRequest(responsible_IPAddress, responsible_port));
                }catch(UnknownHostException e){
                    e.printStackTrace();
                }catch(IOException e){
                    e.printStackTrace();
                }
                
            }
                     
            out.close();
            out.flush();
            output.close();
            
            System.out.println("GET Request from TestClient processed: for Store with ID: " + 
            IP_address + " and port: " + storePort );
            clientSocket.close();
        } catch (IOException e) {
            //report exception somewhere.
            e.printStackTrace();
        }
    }
} 
