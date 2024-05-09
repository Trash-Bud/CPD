package example.cpd;

import java.io.*;
import java.net.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public class TestClient {

    private Socket serverSocket; //TCP/IP messages with server

    private String IP_addr;
    private int port;
    private byte[] buf;
    private List<String> optional_args = new ArrayList<>();

    public TestClient(String nope_ap, String operation, List<String> optional_args) throws SocketException, UnknownHostException, IOException {
        
        String[] parts = nope_ap.split(":");
        IP_addr = parts[0]; 
        try{
            port = Integer.parseInt(parts[1]);
        }
        catch(NumberFormatException e){
            e.printStackTrace();
        }
        for(var arg: optional_args){
            this.optional_args.add(arg);
        }
        serverSocket = new Socket(IP_addr, port);
    
    }


    public String sendEcho(String msg) throws IOException{
        OutputStream output = serverSocket.getOutputStream();
        PrintWriter writer = new PrintWriter(output, true);
        writer.println(msg);
        InputStream input = serverSocket.getInputStream();
        BufferedReader reader = new BufferedReader(new InputStreamReader(input));
 
        String storeResponse = reader.readLine();
        
        output.flush();
        return storeResponse;
        

        
    }

    public void sendJoin() throws IOException{
        System.out.println("address: " + IP_addr + "port: " + port);
        String msg = "JOIN REQUEST FROM : " + IP_addr + " " + port;        
        sendEcho(msg);
    }

    public void sendLeave() throws IOException{
        System.out.println("address: " + IP_addr + "port: " + port);
        String msg = "LEAVE REQUEST FROM : " + IP_addr + " " + port;        
        sendEcho(msg);
    }

    public void sendGet() throws IOException{
        System.out.println("address: " + IP_addr + "port: " + port);
        String fileHash = this.optional_args.get(0);
        String msg = "GET REQUEST FROM : " + IP_addr + " " + port + " " + fileHash;  
        OutputStream output = serverSocket.getOutputStream();
        PrintWriter writer = new PrintWriter(output, true);
        writer.println(msg);
        InputStream input = serverSocket.getInputStream();
        BufferedReader reader = new BufferedReader(new InputStreamReader(input));
 
        String storeResponse = reader.readLine();
        System.out.println(storeResponse); //ACKNOWLEDGE THAT GET REQUEST WAS RECEIVED
        storeResponse = reader.readLine(); //GETS THE RETURNING VALUE
        System.out.println(storeResponse);
        output.flush();

    }
    public void sendDelete() throws IOException{
        System.out.println("address: " + IP_addr + "port: " + port);
        String fileHash = this.optional_args.get(0);
        String msg = "DELETE REQUEST FROM : " + IP_addr + " " + port + " " + fileHash;  
        OutputStream output = serverSocket.getOutputStream();
        PrintWriter writer = new PrintWriter(output, true);
        writer.println(msg);
        InputStream input = serverSocket.getInputStream();
        BufferedReader reader = new BufferedReader(new InputStreamReader(input));
 
        String storeResponse = reader.readLine();
        System.out.println(storeResponse); //ACKNOWLEDGE THAT DELETE REQUEST WAS RECEIVED
        storeResponse = reader.readLine(); //GETS THE RETURNING VALUE
        System.out.println(storeResponse);
        output.flush();

    }
    public void sendPut() throws IOException{
        System.out.println("address: " + IP_addr + "port: " + port);
        String filename = this.optional_args.get(0);
        String shaHash = Membership.generateHash(filename);
        String msg = "PUT REQUEST FROM : " + IP_addr + " " + port + " " + shaHash + " " + "REFUSE_IF_NOT_RESPONSIBLE";        
        
        
        OutputStream output = serverSocket.getOutputStream();
        PrintWriter writer = new PrintWriter(output, true);
        writer.println(msg);
        InputStream input = serverSocket.getInputStream();
        BufferedReader reader = new BufferedReader(new InputStreamReader(input));
 
        String storeResponse = reader.readLine();
        System.out.println(storeResponse);


        //the requested node is not in the cluster T_T
        if(storeResponse.startsWith("Unable to fullfill")){
            
            return;
        }else{
            writer.println("START TCP FILE INFO TRANSFER FROM IP_ADRESS, PORT : " + IP_addr + " " + port );
        }
        
        Path filePath = new File(filename).toPath();       
        String fileContent = Files.readString(filePath);
        
        writer.println(IP_addr + " " + port + " " + fileContent);
        

        writer.println("ENDED TCP FILE INFO TRANSFER FROM : " + IP_addr + " " + port);
        storeResponse = reader.readLine();
        System.out.println(storeResponse);
        output.flush();
    }
    public void close() throws IOException{
        serverSocket.close();
    }

    public static void main(String[] args) {
        if (args.length < 2){
            System.out.println("Correct format:  java TestClient <IP address>:<port number> <operation> [<opnd>]");
            return;
        }
        String node_ap = args[0];
        String op = args[1];
        List<String> optional_args = new ArrayList<>();
        for (int i = 2; i < args.length; i++){
            optional_args.add(args[i]);
        }
        try{
            TestClient testClient = new TestClient(node_ap, op, optional_args);
            if(op.equals("join"))
                testClient.sendJoin();
            if(op.equals("leave"))
                testClient.sendLeave();
            if(op.equals("put")){
                testClient.sendPut();
            }
            if(op.equals("get")){
                testClient.sendGet();
            }
            if(op.equals("delete")){
                testClient.sendDelete();
            }
            testClient.close();
        }catch(Exception e  ){
            e.printStackTrace();
        }
        
    }
}