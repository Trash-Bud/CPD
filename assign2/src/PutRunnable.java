package example.cpd;

import java.io.*;
import java.net.ServerSocket;
import java.io.*;
import java.net.*;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.*;
/**

 */
public class PutRunnable implements Runnable{

    protected Socket clientSocket = null;
    protected String testClientText   = null;

    private String folder_path;
    private String IP_address;
    private int storePort;
    private String fileHashValue;
    private String put_prefix = "PUT REQUEST FROM : ";
    private File new_file_put;
    
    private String refusal;
    private Membership membershipInfo;
    

    public PutRunnable(Socket clientSocket, String clientText, String folder_path, Membership membershipInfo) {
        this.clientSocket = clientSocket;
        this.testClientText   = clientText;
        this.folder_path = folder_path;
    
        this.membershipInfo = membershipInfo;
        System.out.println("starting a worker to handle a new PUT request!");

    }

    public void put_node(){
        String put_msg = testClientText.substring(put_prefix.length());
        String[] parts = put_msg.split(" ");
        IP_address = parts[0]; 
        storePort = Integer.parseInt(parts[1].trim());
        fileHashValue = parts[2];
        refusal = parts[3];
    }

    public void redirectRequest(String fileContents,String responsible_IPAddress,int responsible_port, String refusal) throws UnknownHostException, IOException{

        try{
            Socket socket = new Socket(responsible_IPAddress, responsible_port);

            System.out.println("oi tamos no redirect request depois de criar o socket");
            OutputStream output = socket.getOutputStream();
            PrintWriter writer = new PrintWriter(output, true);
            
            InputStream input = socket.getInputStream();
            BufferedReader reader = new BufferedReader(new InputStreamReader(input));
            writer.println("PUT REQUEST FROM : " + membershipInfo.getOwnStoreAddress() 
            + " " + membershipInfo.getOwnStorePort() + " " + fileHashValue + " " + refusal);  

            System.out.println("mandamos a mensagem de PUT");
            System.out.println("PUT REQUEST FROM : " + membershipInfo.getOwnStoreAddress() 
            + " " + membershipInfo.getOwnStorePort() + " " + fileHashValue + " " + refusal);
            String storeResponse = reader.readLine();

            System.out.println(storeResponse);

            if(storeResponse.startsWith("Unable to fullfill")){
                System.out.println(storeResponse);
                return;
            }else{
                writer.println("START TCP FILE INFO TRANSFER FROM IP_ADRESS, PORT : " +
                membershipInfo.getOwnStoreAddress() + " " + membershipInfo.getOwnStorePort() + " " + refusal);
            }
            
            
            writer.println( membershipInfo.getOwnStoreAddress()  + " " + membershipInfo.getOwnStorePort() + " " + fileContents);

            writer.println("ENDED TCP FILE INFO TRANSFER FROM : " + membershipInfo.getOwnStoreAddress() 
            + " " + membershipInfo.getOwnStorePort());
        
            System.out.println(storeResponse);
            writer.close();
            output.flush();
            output.close();
            writer.close();
  
        } catch (UnknownHostException ex) {
  
            System.out.println("Server not found: " + ex.getMessage());

          } catch (IOException ex) {

            System.out.println("I/O error: " + ex.getMessage());
          }
        catch(Exception ex){
            System.out.println("Exception error: " + ex.getMessage());
          }

        
    }

    public void run() {
        try {
            OutputStream output = clientSocket.getOutputStream();
            PrintWriter out = new PrintWriter(output, true);
            String write_put_file;
            
            put_node();
          
            //send this back to the TestClient so we know that the join request has been acknowledged
            out.println("Received the PUT request from TestClient for Store with ID: " + 
            IP_address + " and port: " + storePort + " and HASH: " + fileHashValue + " " + refusal);
            
            
            InputStream input  = clientSocket.getInputStream();
            BufferedReader reader = new BufferedReader(new InputStreamReader(input));
            String inputLine;
            String[] parts;

            String fileContents = "";
            //get the fileContent from TestClient
            
            while ((inputLine = reader.readLine()) != null) {
                System.out.println("received TCP info to reconstruct the file: " + inputLine);
          
                if(inputLine.startsWith("START TCP FILE INFO TRANSFER FROM IP_ADRESS, PORT : ")){
                    out.println("ACK");
                }
                else if(inputLine.startsWith("ENDED TCP FILE INFO TRANSFER FROM : ")){
                 
                    System.out.println("ended tcp file info transfer FROM: " + IP_address + ":" + storePort);
                    out.println("bye");
                    break;
                }else{
                  
                    if(inputLine.equals("")){
                        continue;
                    }
                    parts = inputLine.split(" ");
                    String line = parts[2];
                    fileContents = fileContents + line + "\n"; 
                    
                }

            }
            
            //now we have the contents of the file, but we do not know if this node is responsible
            //for this key-value
            String responsible_node_id = this.membershipInfo.getResponsibleNode(fileHashValue);
            String responsible_IPAddress = responsible_node_id.split(":")[0];
            int responsible_port = Integer.parseInt(responsible_node_id.split(":")[1]);
            if(responsible_node_id.equals(membershipInfo.getOwnNodeId()) || refusal.equals("DO_NOT_REFUSE_IF_NOT_RESPONSIBLE")){
                //we are the responsible node, store it in our filesystem OR this is an unrefusable put
                String file_path = folder_path +  "\\" + fileHashValue + ".txt";
                File puttedFile = new File(file_path);
                puttedFile.createNewFile();
                FileWriter myWriter = new FileWriter(file_path);
                myWriter.write(fileContents);
                myWriter.close();

                //REPLICATION - ensure we propagate this FILE to another 2 stores

                List<String> replicateNodeIds = membershipInfo.getTwoActiveNodeIds();
                for(String nodeId : replicateNodeIds){
                    String[] partsId = nodeId.split(":");
                    String nodeAddress = partsId[0];
                    int nodePort = Integer.parseInt(partsId[1]);
                    redirectRequest(fileContents, nodeAddress, nodePort, "DO_NOT_REFUSE_IF_NOT_RESPONSIBLE");
                }
                

            }else{
                //we are not the responsible node, send the info we have via TCP to the responsible man
                System.out.println("We are not the responsible for this PUT, redirecting info to the responsible STORE now.");
                try{
                    System.out.println(">>>>>      REDIRECT TO RESPONSIBLE NODE :  + " + responsible_IPAddress + ":" + responsible_port +
                     "            <<<<<<");
                    redirectRequest(fileContents, responsible_IPAddress, responsible_port, "REFUSE_IF_NOT_RESPONSIBLE");
                }catch(UnknownHostException e){
                    e.printStackTrace();
                }catch(IOException e){
                    e.printStackTrace();
                }
                
                
            }
                
            out.println("PUT Request from TestClient processed: for Store with ID: " + 
            IP_address + " and port: " + storePort);
            out.flush();

            out.close();

            output.close();
            
            System.out.println("PUT Request from TestClient processed: for Store with ID: " + 
            IP_address + " and port: " + storePort );
            
            clientSocket.close();
        } catch (IOException e) {
            //report exception somewhere.
            e.printStackTrace();
        }
    }
} 
