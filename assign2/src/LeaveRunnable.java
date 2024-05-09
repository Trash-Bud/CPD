package example.cpd;

import java.io.InputStream;
import java.io.BufferedReader;
import java.io.OutputStream;
import java.io.IOException;
import java.net.*;

/**

 */
public class LeaveRunnable implements Runnable{

    protected Socket clientSocket = null;
    protected String testClientText   = null;
    private String IP_address = null;
    private int storePort;
    private String leave_prefix = "LEAVE REQUEST FROM : ";

    public LeaveRunnable(Socket clientSocket, String clientText) {
        this.clientSocket = clientSocket;
        this.testClientText   = clientText;
        
        System.out.println("starting a worker to handle a new leave request!");
    }

    public void leave_node(){

        String leave_msg = testClientText.substring(leave_prefix.length());
        String[] parts = leave_msg.split(" ");
        IP_address = parts[0]; 
        storePort = Integer.parseInt(parts[1].trim());

      
        
    }

    public void run() {
        try {
            
            /*
                esta classe vai tratar depois de establecer uma conexãO TCP 
                com o sucessor deste node no cluster e transferir-lhe as keys-object ´
                pair que lhe pertencem 
            */
            leave_node();
            OutputStream output = clientSocket.getOutputStream();
            
            //send this back to the TestClient so we know that the join request has been acknowledged
            output.write(("Received the LEAVE request from TestClient for Store with ID: " + 
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
