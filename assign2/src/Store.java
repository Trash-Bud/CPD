package example.cpd;

import java.io.*;
import java.net.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import java.text.SimpleDateFormat;

public class Store implements Runnable {

    //private List<Client> clients = new ArrayList<>();

    private String multicastAdress;
    private int multicastPort;

    private String join_prefix = "JOIN REQUEST FROM : ";
    private String leave_prefix = "LEAVE REQUEST FROM : ";
    private String put_prefix = "PUT REQUEST FROM : ";
    private String get_prefix = "GET REQUEST FROM : ";
    private String delete_prefix = "DELETE REQUEST FROM : ";

    protected int          serverPort   = 8080;
    private String         serverAddress = null;

    protected int          TCPmembershipPort = 8080;

    private String membershipLogFileName;

    protected ServerSocket serverSocket = null;
    protected boolean      isStopped    = false;
    protected Thread       runningThread= null;
    protected MulticastListener   multicastClusterListener = null;


    private String folder_path;

    private Membership membershipInfo;
    protected ExecutorService threadPool =
        Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());


    public Store(String IP_mcast_addr, String IP_mcast_port,String node_id,String Store_port) {
        this.serverAddress = node_id;
        folder_path = System.getProperty("user.dir") + "\\CPD_STORES_FILE_SYSTEM\\" + this.serverAddress;
        try{
            ServerSocket localServerSocket = new ServerSocket(0);
            this.TCPmembershipPort = localServerSocket.getLocalPort();
            localServerSocket.close();
        }catch(IOException e){
            e.printStackTrace();
        }
        
        
        try{
             this.multicastPort = Integer.parseInt(IP_mcast_port);
        }catch(NumberFormatException e){
            System.out.println("Store Port is not a Number!");
            e.printStackTrace();
        }

        try {
            serverPort = Integer.parseInt(Store_port);
            System.out.println("port: "+ serverPort + " address: " + node_id);
        } 
        catch(NumberFormatException e){
            System.out.println("Store Port is not a Number!");
            e.printStackTrace();
        }

       
        this.multicastAdress = IP_mcast_addr;
        String log_dir = System.getProperty("user.dir") + "\\store_logs\\";
        this.membershipLogFileName = log_dir + this.serverAddress + " " + this.serverPort + " 's membership log" + ".txt";
        
        membershipInfo = new Membership(this.membershipLogFileName, serverPort, serverAddress, multicastPort, multicastAdress, this.folder_path);

        //create a directory where we store files stored in this node
        try{
            Files.createDirectories(Paths.get(folder_path));
            Files.createDirectories(Paths.get(log_dir));
        }catch(IOException e){
            e.printStackTrace();
        }
        
    }

    public void close() throws IOException{
        serverSocket.close();
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

    private String getClientMessage(Socket clientSocket) throws IOException{
        InputStream input  = clientSocket.getInputStream();
        BufferedReader reader = new BufferedReader(new InputStreamReader(input));

        String inputString = reader.readLine();
        System.out.println("received : " + inputString);
        return inputString;
    }



    private void processRequest(String clientString, Socket clientSocket) throws IOException{
       
        if(clientString.startsWith(join_prefix)){
            if(multicastClusterListener != null){
                System.out.println("<< ALREADY IN CLUSTER >>");
                return;
            }
            
        
            membershipInfo.createMembershipLog(); 
            membershipInfo.incrementMembershipCounter();
            this.threadPool.execute(
                new JoinRunnable(clientSocket,
                    clientString));
            
            
            //começa a ouvir TCP connections num IP especificado
            this.threadPool.execute(new 
            TCPGetMembershipInfo(this.TCPmembershipPort, this.membershipLogFileName, this.membershipInfo));
            
            multicastClusterListener = new MulticastListener(multicastAdress, multicastPort, serverAddress,serverPort ,
             TCPmembershipPort, membershipLogFileName, membershipInfo);
            //START ACCEPTING UDP REQUESTS AT MULTICAST_ADDRESS 
            this.threadPool.execute(multicastClusterListener);

            this.threadPool.execute(new SendLeaderInfo(membershipInfo));

          
        }else if(multicastClusterListener == null){
            System.out.println("<< NOT IN CLUSTER >>");
            OutputStream output = clientSocket.getOutputStream();
            PrintWriter out = new PrintWriter(output, true);
            out.println("Unable to fullfill the request from TestClient for Store with ID: " + 
            this.serverAddress + " and port: " + this.serverPort);

            out.close();
            output.close();
        }
        else if(clientString.startsWith(leave_prefix)){
                //leave the cluster, stop listening for the UDP messages of the others

            membershipInfo.incrementMembershipCounter();
            this.threadPool.execute(
            new LeaveRunnable(clientSocket,
                clientString));
            
            this.threadPool.execute(new TCPKeyTransfer(membershipInfo, true));
            //if the cluster leader is leaving, send a change cluster leader message to the second most updated activeNode
            membershipInfo.onLeaderLeave();
            membershipInfo.deleteAllActiveNodes();
            multicastClusterListener.stop();
            multicastClusterListener = null;
            System.out.println("LEAVING THE CLUSTER NOW");
        
        
        }else if(clientString.startsWith(put_prefix)){
            
            this.threadPool.execute(new PutRunnable(clientSocket, clientString, folder_path, membershipInfo));

        }else if(clientString.startsWith(get_prefix)){
            this.threadPool.execute(new GetDeleteRunnable(clientSocket, clientString, folder_path, membershipInfo, get_prefix));

        }else if(clientString.startsWith(delete_prefix)){
            this.threadPool.execute(new GetDeleteRunnable(clientSocket, clientString, folder_path, membershipInfo, delete_prefix));
        }
    }

    public void run() {
        synchronized(this){
            this.runningThread = Thread.currentThread();
        }

        openServerSocket();
        System.out.println("open Server socket");

        while(! isStopped()){
            Socket clientSocket = null;
            String receivedFromClient = null;

            try {
                serverSocket.close();
                clientSocket = null;
                openServerSocket();
                clientSocket = this.serverSocket.accept();
                receivedFromClient = getClientMessage(clientSocket);
                System.out.println(">>>  received from Client: " + receivedFromClient);
            } catch (IOException e) {
                if(isStopped()) {
                    System.out.println("Server Stopped.") ;
                    break;
                }
                throw new RuntimeException(
                    "Error accepting client connection", e);
            }
            try{
                processRequest(receivedFromClient, clientSocket);
             
            }
            catch(IOException e){
                e.printStackTrace();
            }
            
    
        }
        this.threadPool.shutdown();
        System.out.println("Server Stopped.") ;
       
    }

    private synchronized boolean isStopped() {
        return this.isStopped;
    }

    public static void main(String[] args) {
        
        if (args.length != 4){
            System.out.println("Wrong format: java example.cpd.Store <IP_mcast_addr> <IP_mcast_port> <node_id>  <Store_port>");       
            return;
        }
        String IP_mcast_addr = args[0];
        String IP_mcast_port = args[1];
        String node_id = args[2];
        String Store_port = args[3]; 
        Store store = new Store(IP_mcast_addr, IP_mcast_port, node_id, Store_port);

        new Thread(store).start();
        System.out.println("server started");
        
        
    }

}