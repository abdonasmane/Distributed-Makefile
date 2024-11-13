import java.io.*;
import java.net.*;
import java.nio.ByteBuffer;

public class Ping {
    public static void main(String[] args) {
        if (args.length != 3) {
            System.out.println("Usage: java Ping <MESSAGE-SIZE> <SERVER_IP> <SERVER_PORT>");
            return;
        }

        String machineB = args[1];
        int port;
        int messageSize;
        try {
            messageSize = Integer.parseInt(args[0]);
            if (messageSize < 0) {
                System.out.println("ERROR: MESSAGE-SIZE must be > 0");
                return;
            }
            port =  Integer.parseInt(args[2]);
            if (port < 1 || port > 65535) {
                System.out.println("ERROR: SERVER_PORT must be between 1 and 65535.");
                return;
            }
        } catch (NumberFormatException e) {
            System.out.println("ERROR: Invalid SERVER_PORT or MESSAGE-SIZE -- MESSAGE_SIZE should not be greater than INT_MAX-10");
            return;
        }

        // construct message
        byte separator = '\t';
        byte[] message = new byte[8+1+0+1+messageSize];
        // insert messageSize in the first 8 bytes if the packet
        ByteBuffer.wrap(message).putLong(messageSize);
        // insert separator
        message[8] = separator;
        // No file name
        // insert separator
        message[9] = separator;
        // insert message content
        for (int i = 10; i < messageSize + 10; i++) {
            message[i] = 0;
        }

        // Start time
        long startTime = System.nanoTime();

        try (Socket socket = new Socket(machineB, port);
            OutputStream out = socket.getOutputStream();
            InputStream in = socket.getInputStream()
        ) {
            // Send the message to Machine B
            out.write(message);
            out.flush();
            
            // Wait for the 1-byte acknowledgment from Machine B
            in.read();
            // End time
            long endTime = System.nanoTime();

            // Calculate RTT and debit
            long rtt = endTime - startTime;

            // RTT
            System.out.println("Round Trip Time (RTT): " + (rtt/1e9) + " seconds");
        } catch (UnknownHostException u) {
            System.out.println("Network Error : " + u);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
