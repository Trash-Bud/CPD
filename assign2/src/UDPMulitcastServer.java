package example.cpd;

import java.io.*;
import java.net.*;
import java.util.*;

public class UDPMulitcastServer {

    private String multicastAddress;
    private int multicastPort;

    UDPMulitcastServer(String multicastAddress, int multicastPort){
        this.multicastAddress = multicastAddress;
        this.multicastPort = multicastPort;
    }

    public void sendMulticastUDPMessage(String message) throws IOException {
        DatagramSocket socket = new DatagramSocket();
        System.out.println("[Sending UDP message!] >> " + message);

        InetAddress group = InetAddress.getByName(multicastAddress);

        byte[] msg = message.getBytes();
        DatagramPacket packet = new DatagramPacket(msg, msg.length,
            group, multicastPort);

        socket.send(packet);
    
        socket.close();
   }
}
