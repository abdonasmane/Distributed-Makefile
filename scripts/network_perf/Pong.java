import java.io.*;
import java.net.*;
import java.nio.ByteBuffer;

public class Pong {
    public static void main(String[] args) {
        if (args.length != 2) {
            System.out.println("Usage: java Pong <CHUNK_SIZE> <SERVER_PORT>");
            return;
        }

        int chunkSize;
        int port;
        try {
            chunkSize = Integer.parseInt(args[0]);
            if (chunkSize <= 0) {
                System.out.println("ERROR: CHUNK_SIZE must be greater than 0.");
                return;
            }
            port =  Integer.parseInt(args[1]);
            if (port < 1 || port > 65535) {
                System.out.println("ERROR: SERVER_PORT must be between 1 and 65535.");
                return;
            }
        } catch (NumberFormatException e) {
            System.out.println("ERROR: Invalid SERVER_PORT or CHUNK_SIZE");
            return;
        }

        try (ServerSocket serverSocket = new ServerSocket(port)) {
            while (true) {
                // Accept a connection from Machine A
                try (Socket socket = serverSocket.accept()) {
                    InputStream in = socket.getInputStream();
                    OutputStream out = socket.getOutputStream();
                    // detect message size
                    byte[] sizeBuffer = new byte[8];
                    in.read(sizeBuffer);
                    long messageSize = ByteBuffer.wrap(sizeBuffer).getLong();
                    // consume two separators
                    in.read();
                    in.read();
                    // Read the incoming message
                    byte[] buffer = new byte[chunkSize];
                    int bytesRead;
                    long totalBytes = 0;
                    while (totalBytes < messageSize) {
                        // Simulate reading message (do nothing)
                        bytesRead = in.read(buffer);
                        totalBytes += bytesRead;
                        // String receivedMessage = new String(buffer, 0, bytesRead);
                        // System.out.println("Received " + bytesRead + " bytes from Machine A : \n" + receivedMessage);
                    }
                    // Send a 1-byte acknowledgment to Machine A (ping-pong)
                    out.write(1);
                    out.flush();
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
