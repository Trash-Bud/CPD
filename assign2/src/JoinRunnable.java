package example.cpd;

import java.io.InputStream;
import java.io.BufferedReader;
import java.io.OutputStream;
import java.io.IOException;
import java.net.Socket;

/**

 */
public class JoinRunnable implements Runnable{

    protected Socket clientSocket = null;
    protected String testClientText   = null;

    private String IP_address;
    private int storePort;
    private String join_prefix = "JOIN REQUEST FROM : ";

    public JoinRunnable(Socket clientSocket, String clientText) {
        this.clientSocket = clientSocket;
        this.testClientText   = clientText;
        System.out.println("starting a worker to handle a new request!");
    }

    public void join_node(){
        String join_msg = testClientText.substring(join_prefix.length());
        String[] parts = join_msg.split(" ");
        IP_address = parts[0]; 
        storePort = Integer.parseInt(parts[1].trim());
    }

    public void run() {
        try {
            
            join_node(); 
            OutputStream output = clientSocket.getOutputStream();
            
            //send this back to the TestClient so we know that the join request has been acknowledged
            output.write(("Received the JOIN request from TestClient for Store with ID: " + 
            IP_address + " and port: " + storePort ).getBytes());
            
            output.flush();
            output.close();
            
            System.out.println("Request from TestClient processed: for Store with ID: " + 
            IP_address + " and port: " + storePort );
            
        } catch (IOException e) {
            //report exception somewhere.
            e.printStackTrace();
        }
    }
} 
