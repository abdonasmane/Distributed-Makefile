import java.io.*;
import java.net.*;

public class PongIO {
    public static void main(String[] args) {
        if (args.length != 3) {
            System.out.println("Usage: java PongIO <SERVER_PORT> <PATH_TO_FILE> <CHUNK_SIZE>");
            return;
        }

        int port;
        String filePath = args[1];
        int chunkSize;

        try {
            port = Integer.parseInt(args[0]);
            chunkSize = Integer.parseInt(args[2]);
            if (chunkSize <= 0) {
                System.out.println("ERROR: CHUNK_SIZE must be greater than 0.");
                return;
            }
        } catch (NumberFormatException e) {
            System.out.println("ERROR: Invalid SERVER_PORT or CHUNK_SIZE");
            return;
        }

        System.out.println("Pong server is starting on Port " + port);
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            while (true) {
                // Accept a connection from Machine A
                try (Socket socket = serverSocket.accept()) {
                    InputStream in = socket.getInputStream();
                    OutputStream out = socket.getOutputStream();

                    // Create a file at the given path
                    try (FileOutputStream fileOutputStream = new FileOutputStream(filePath)) {
                        byte[] buffer = new byte[chunkSize];
                        int bytesRead;
                        int totalBytes = 0;

                        // Read the incoming message in chunks
                        while ((bytesRead = in.read(buffer)) != -1) {
                            // Write the received chunk to the file
                            fileOutputStream.write(buffer, 0, bytesRead);
                            totalBytes += bytesRead;
                            System.out.println("Received " + bytesRead + " bytes from Machine A and wrote to file");
                        }
                        // Send a 1-byte acknowledgment to Machine A (ping-pong)
                        out.write(1);
                        out.flush();
                        System.out.println("Acknowledgment sent to Machine A: received " + totalBytes + " bytes in total and wrote to " + filePath);
                    } catch (IOException e) {
                        System.err.println("Error writing to file: " + e.getMessage());
                        e.printStackTrace();
                    }
                } catch (IOException e) {
                    System.err.println("Error while handling client connection: " + e.getMessage());
                    e.printStackTrace();
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
