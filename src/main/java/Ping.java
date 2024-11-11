import java.io.*;
import java.net.*;

public class Ping {
    public static void main(String[] args) {
        if (args.length != 3) {
            System.out.println("Usage: java Ping <MESSAGE-SIZE> <SERVER_IP> <SERVER_PORT>");
            return;
        }

        // Message size in bytes (N)
        int messageSize = Integer.parseInt(args[0]);
        if (messageSize < 0) {
            System.out.println("ERROR: MESSAGE-SIZE must be > 0");
            return;
        }

        String machineB = args[1];
        int port;
        try {
            port =  Integer.parseInt(args[2]);
        } catch (NumberFormatException e) {
            System.out.println("ERROR: Invalid SERVER_PORT");
            return;
        }
        byte[] message = new byte[messageSize];
        for (int i = 0; i < messageSize; i++) {
            message[i] = 'A';
        }

        // Start time
        long startTime = System.nanoTime();

        try (Socket socket = new Socket(machineB, port)) {
            OutputStream out = socket.getOutputStream();
            InputStream in = socket.getInputStream();

            // Send the message to Machine B
            out.write(message);
            out.flush();
            
            // Wait for the 1-byte acknowledgment from Machine B
            in.read();
            System.out.println("Received ACK from Machine B");

            // End time
            long endTime = System.nanoTime();

            // Calculate RTT and debit
            long rtt = endTime - startTime;

            // RTT
            System.out.println("Round Trip Time (RTT): " + (rtt/1e9) + " seconds");
            if (messageSize > 1) {
                // Calculate debit for messages larger than 1 byte
                double debit = (double) messageSize / ((rtt / 2) / 1e9);  // bytes per second
                System.out.println("Debit: " + debit + " bytes/second");
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
