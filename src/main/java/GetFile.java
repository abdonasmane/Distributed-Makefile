import java.io.*;
import java.net.*;
import java.nio.ByteBuffer;

public class GetFile {

    public static boolean retrieveFile(String serverHost, int serverPort, String fileName, String destinationPath) {
        try (Socket socket = new Socket(serverHost, serverPort)) {
            InputStream in = socket.getInputStream();
            OutputStream out = socket.getOutputStream();

            out.write((fileName + "\n").getBytes());
            out.flush();

            // Receive the file size first
            byte[] sizeBuffer = new byte[8];
            in.read(sizeBuffer);
            if (sizeBuffer[0] == '\t') {
                System.err.println("Error : couldn't retrieve file from the server " + serverHost);
                return false;
            }
            long fileSize = ByteBuffer.wrap(sizeBuffer).getLong();

            // Consume separator (tab byte)
            in.read(); 

            // Prepare to receive the file
            byte[] buffer = new byte[100000];  // You can adjust chunk size if needed
            try (FileOutputStream fileOutputStream = new FileOutputStream(destinationPath)) {
                int bytesRead;
                long totalBytesRead = 0;

                // Read the file in chunks and write to the local file
                while (totalBytesRead < fileSize) {
                    bytesRead = in.read(buffer);
                    totalBytesRead += bytesRead;
                    fileOutputStream.write(buffer, 0, bytesRead);
                }
            }

            System.out.println("File '" + fileName + "' received successfully.");
            return true;
        } catch (IOException e) {
            System.err.println("Error connecting to the server: " + e.getMessage());
            return false;
        }
    }

    public static void main(String[] args) {
        // For demonstration purposes, the main method will call the retrieveFile method
        if (args.length != 3) {
            System.out.println("Usage: java GetFile <SERVER_HOST> <SERVER_PORT> <FILE_NAME>");
            return;
        }

        String serverHost = args[0];
        int serverPort = Integer.parseInt(args[1]);
        String fileName = args[2];
        
        // Destination where the file will be saved
        String destinationPath = "received_" + fileName;

        // Call the method to retrieve the file
        retrieveFile(serverHost, serverPort, fileName, destinationPath);
    }
}
