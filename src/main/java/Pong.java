import java.io.*;
import java.net.*;

public class Pong {
    public static void main(String[] args) {
        if (args.length != 1) {
            System.out.println("Usage: java Pong <SERVER_PORT>");
            return;
        }

        int port;
        try {
            port =  Integer.parseInt(args[0]);
        } catch (NumberFormatException e) {
            System.out.println("ERROR: Invalid SERVER_PORT");
            return;
        }

        System.out.println("Pong server is starting on Port " + port);
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            while (true) {
                // Accept a connection from Machine A
                try (Socket socket = serverSocket.accept()) {
                    InputStream in = socket.getInputStream();
                    OutputStream out = socket.getOutputStream();

                    // Read the incoming message
                    byte[] buffer = new byte[1024];
                    int bytesRead;
                    int totalBytes = 0;
                    while (in.available() > 0) {
                        // Simulate reading message (do nothing)
                        bytesRead = in.read(buffer);
                        totalBytes += bytesRead;
                        String receivedMessage = new String(buffer, 0, bytesRead);
                        System.out.println("Received " + bytesRead + " bytes from Machine A : \n" + receivedMessage);
                    }
                    // Send a 1-byte acknowledgment to Machine A (ping-pong)
                    out.write(1);
                    out.flush();
                    System.out.println("Acknowledgment sent to Machine A : received " + totalBytes + " bytes in total");
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
