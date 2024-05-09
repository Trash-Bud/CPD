package example.cpd;

import java.io.*;
import java.util.*;
import java.net.*;
import java.nio.file.Files;
import java.nio.file.Path;


public class TCPKeyTransfer implements Runnable {

    private Membership membershipInfo;
    private Socket socket; //TCP/IP messages with sucessor

    private String sucessor_IPAdress;
    private int sucessor_port;
    private String succesor_nodeId;

    private boolean leaving; 
    TCPKeyTransfer(Membership membershipInfo, boolean leaving){
        this.membershipInfo = membershipInfo;
        this.succesor_nodeId = this.membershipInfo.getOwnSucessor();
        String[] IdParts = this.succesor_nodeId.split(":");
        sucessor_IPAdress = IdParts[0];
        this.leaving = leaving;
        sucessor_port = Integer.parseInt(IdParts[1]);
    }

    public List<String> keysToTransfer(){
        List<String> keysTotransfer = new ArrayList<>();
        File dir = new File(membershipInfo.getFolderPath());
        File[] directoryListing = dir.listFiles();
        String fileName;
        if (directoryListing != null) {
            for (File child : directoryListing) {
                fileName = child.getName();
                fileName = fileName.substring(0, fileName.length() - 4);
                if(this.membershipInfo.getResponsibleNode(fileName).equals(this.succesor_nodeId) || this.leaving){
                    keysTotransfer.add(fileName + ".txt");
                    System.out.println("We will be transfering to sucessor node: " + fileName);
                  
                }
            }
        } 

        return keysTotransfer;
    }

    @Override
    public void run() {

        try{
            if(this.membershipInfo.getOwnNodeId().equals(succesor_nodeId)){
                System.out.println("There are no sucessors!");
                return;
            }

            socket = new Socket(sucessor_IPAdress, sucessor_port);
            OutputStream output = socket.getOutputStream();
            PrintWriter writer = new PrintWriter(output, true);
            List<String> fileNamesForTransfer = this.keysToTransfer();
            InputStream input = socket.getInputStream();
            BufferedReader reader = new BufferedReader(new InputStreamReader(input));


            for(String filename: fileNamesForTransfer){
                String shaHash = filename.substring(0, filename.length() - 4);
                String msg = "PUT REQUEST FROM : " + this.membershipInfo.getOwnStoreAddress() + " " +
                this.membershipInfo.getOwnStorePort() + " " + shaHash + " " + "DO_NOT_REFUSE_IF_NOT_RESPONSIBLE";        
        
                writer.println(msg);
            
                String storeResponse = reader.readLine();
                System.out.println(storeResponse);


                //the requested node is not in the cluster T_T
                if(storeResponse.startsWith("Unable to fullfill")){        
                    return;
                }else{
                    writer.println("START TCP FILE INFO TRANSFER FROM IP_ADRESS, PORT : " + this.membershipInfo.getOwnStoreAddress() + " " +
                    this.membershipInfo.getOwnStorePort());
                }
                
                File fileToSend = new File(this.membershipInfo.getFolderPath() + "\\" + filename);
                Path filePath = fileToSend.toPath();       
                String fileContent = Files.readString(filePath);
                
                writer.println(this.membershipInfo.getOwnStoreAddress() + " " +
                this.membershipInfo.getOwnStorePort()+ " " + fileContent);
                
                //delete the file 
                fileToSend.delete();

                writer.println("ENDED TCP FILE INFO TRANSFER FROM : " + this.membershipInfo.getOwnStoreAddress() + " " +
                this.membershipInfo.getOwnStorePort());

                storeResponse = reader.readLine();
                System.out.println(storeResponse);
            }

            output.flush();
            output.close();
            socket.close();

        }
        catch(IOException e){
            e.printStackTrace();
        }
        
    
 
        
    }
}
