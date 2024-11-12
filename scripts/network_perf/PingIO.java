import java.io.*;
import java.net.*;
import java.nio.ByteBuffer;

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

        File file = new File(filePath);
        long fileSize;
        String fileName;
        // Check if the file exists and get the size
        if (file.exists() && file.isFile()) {
            // Get the file size in bytes
            fileSize = file.length();
            fileName = file.getName();
            // Print the file size
            // System.out.println("File size: " + fileSize + " bytes");
        } else {
            System.out.println("ERROR: File does not exist or is not a valid file.");
            return;
        }

        // Start time
        long startTime = System.nanoTime();

        try (Socket socket = new Socket(machineB, port);
             FileInputStream fileInputStream = new FileInputStream(file);
             BufferedInputStream bufferedInputStream = new BufferedInputStream(fileInputStream);
             OutputStream out = socket.getOutputStream();
             InputStream in = socket.getInputStream()) {

            // construct message
            byte separator = '\t';
            byte[] fileNameBytes = fileName.getBytes(); // Convert file name to bytes
            byte[] message = new byte[chunkSize];

            // Insert the file size into the first 8 bytes of the message
            ByteBuffer.wrap(message).putLong(fileSize);

            // Insert the separator after the file size
            message[8] = separator;

            // Insert the file name after the separator
            System.arraycopy(fileNameBytes, 0, message, 9, fileNameBytes.length);

            // Insert another separator after the file name
            message[9 + fileNameBytes.length] = separator;
            
            int offset = 10 + fileNameBytes.length;

            bufferedInputStream.read(message, offset, message.length - offset);
            int bytesRead = chunkSize < offset + fileSize ? chunkSize : offset + (int)fileSize;
            do {
                out.write(message, 0, bytesRead);
                out.flush();
            } while ((bytesRead = bufferedInputStream.read(message)) != -1);
            // Wait for the 1-byte acknowledgment from Machine B
            in.read();
            // System.out.println("All chunks sent and ACKs received");

            // End time
            long endTime = System.nanoTime();

            // Calculate total RTT
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
