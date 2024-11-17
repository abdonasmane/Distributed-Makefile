import java.io.*;
import java.net.*;
import java.nio.ByteBuffer;

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
                    // consume separator
                    in.read();
                    // find file name
                    byte[] fileNameBytes = new byte[256];
                    int index = 0;
                    byte r; 
                    while ((r = (byte)in.read()) != '\t') {
                        fileNameBytes[index] = r;
                        index++;
                    }
                    String fileName = new String(fileNameBytes, 0, index);
                    // Create a file at the given path
                    try (FileOutputStream fileOutputStream = new FileOutputStream(filePath+fileName)) {
                        byte[] buffer = new byte[chunkSize];
                        int bytesRead;
                        long totalBytes = 0;
                        // Read the incoming message in chunks
                        while (totalBytes < messageSize) {
                            // Write the received chunk to the file
                            bytesRead = in.read(buffer);
                            totalBytes += bytesRead;
                            fileOutputStream.write(buffer, 0, bytesRead);
                        }
                        // Send a 1-byte acknowledgment to Machine A (ping-pong)
                        out.write(1);
                        out.flush();
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
