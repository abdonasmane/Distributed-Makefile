import java.io.*;
import java.net.*;

public class PingIO {
    public static void main(String[] args) {
        if (args.length != 4) {
            System.out.println("Usage: java Ping <PATH_TO_FILE> <CHUNK_SIZE> <SERVER_IP> <SERVER_PORT>");
            return;
        }

        String filePath = args[0];
        String machineB = args[2];

        int port;
        int chunkSize;
        try {
            chunkSize = Integer.parseInt(args[1]);
            port =  Integer.parseInt(args[3]);
            if (chunkSize <= 0) {
                System.out.println("ERROR: CHUNK_SIZE must be greater than 0.");
                return;
            }
        } catch (NumberFormatException e) {
            System.out.println("ERROR: Invalid SERVER_PORT or CHUNK_SIZE");
            return;
        }

        // Start time
        long startTime = System.nanoTime();

        try (Socket socket = new Socket(machineB, port);
             FileInputStream fileInputStream = new FileInputStream(filePath);
             BufferedInputStream bufferedInputStream = new BufferedInputStream(fileInputStream);
             OutputStream out = socket.getOutputStream();
             InputStream in = socket.getInputStream()) {

            byte[] buffer = new byte[chunkSize];
            int bytesRead;

            while ((bytesRead = bufferedInputStream.read(buffer)) != -1) {
                // Send the chunk to Machine B
                System.out.println("Sent " + bytesRead);
                out.write(buffer, 0, bytesRead);
                out.flush();
            }
            // Wait for the 1-byte acknowledgment from Machine B
            in.read();
            System.out.println("All chunks sent and ACKs received");

            // End time
            long endTime = System.nanoTime();

            // Calculate total RTT
            long rtt = endTime - startTime;

            // RTT
            System.out.println("Round Trip Time (RTT): " + (rtt/1e9) + " seconds");

            // Calculate throughput (debit) for the entire file
            double totalSizeInBytes = new File(filePath).length();
            double debit = totalSizeInBytes / (rtt / 1e9);  // bytes per second
            System.out.println("Debit: " + debit + " bytes/second");

        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
