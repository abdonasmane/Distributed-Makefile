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
        long messageSize;
        try {
            messageSize = Long.parseLong(args[0]);
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
            System.out.println("ERROR: Invalid SERVER_PORT or MESSAGE-SIZE");
            return;
        }

        // construct message
        byte separator = '\t';
        byte[] message = new byte[8+1+0+1];
        // insert messageSize in the first 8 bytes if the packet
        ByteBuffer.wrap(message).putLong(messageSize);
        // insert separator
        message[8] = separator;
        // No file name
        // insert separator
        message[9] = separator;
        

        try (Socket socket = new Socket(machineB, port);
            OutputStream out = socket.getOutputStream();
            InputStream in = socket.getInputStream()
        ) {
            socket.setTcpNoDelay(true);
            // Start time
            long startTime = System.nanoTime();
            // Send header to Machine B
            out.write(message);
            long remaining = messageSize;
            int chunkSize = (int) Math.min(remaining, 2_100_000_000);
            byte[] messageChunk = new byte[chunkSize];

            while (remaining > 0) {
                out.write(messageChunk, 0, chunkSize);
                remaining -= chunkSize;
                chunkSize = (int) Math.min(remaining, 2_100_000_000);
            }

            // Flush all data
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
