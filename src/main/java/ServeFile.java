import java.io.*;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ServeFile {

    private static final ExecutorService threadPool = Executors.newFixedThreadPool(200);
    public static void main(String[] args) {
        if (args.length != 2) {
            System.out.println("Usage: java ServeFile <SERVER_PORT> <DIRECTORY_TO_SERVE>");
            return;
        }

        int port;
        String directoryPath = args[1];  // Directory from which files will be served
        try {
            port = Integer.parseInt(args[0]);
            if (port < 1 || port > 65535) {
                System.out.println("ERROR: SERVER_PORT must be between 1 and 65535.");
                return;
            }
        } catch (NumberFormatException e) {
            System.out.println("ERROR: Invalid SERVER_PORT.");
            return;
        }

        // Make sure the directory exists
        File directory = new File(directoryPath);
        if (!directory.exists() || !directory.isDirectory()) {
            System.out.println("ERROR: Invalid directory path.");
            return;
        }

        try (ServerSocket serverSocket = new ServerSocket(port)) {
            System.out.println("Server started on port " + port);
            while (true) {
                // Accept a connection from a client
                Socket socket = serverSocket.accept();

                // Handle the client in a separate thread
                threadPool.submit(new ClientHandler(socket, directoryPath));
            }
        } catch (IOException e) {
            System.err.println("Error in ServeFile server: " + e.getMessage());
        }
    }

    // Inner class to handle client connections
    private static class ClientHandler implements Runnable {
        private final Socket socket;
        private final String directoryPath;

        public ClientHandler(Socket clientSocket, String directoryPath) {
            this.socket = clientSocket;
            this.directoryPath = directoryPath;
        }

        @Override
        public void run() {
            try (
                InputStream in = socket.getInputStream();
                OutputStream out = socket.getOutputStream();
            ) {
                // Read the requested file name (terminated by a newline or other separator)
                byte[] fileNameBytes = new byte[256];
                int index = 0;
                byte r;
                while ((r = (byte) in.read()) != '\n' && r != -1) {
                    fileNameBytes[index] = r;
                    index++;
                }
                String fileName = new String(fileNameBytes, 0, index);
                // System.out.println("Serving file " + fileName + " to " + socket.getInetAddress() + " from " + InetAddress.getLocalHost().getHostAddress());

                // Check if the file exists in the specified directory
                Path filePath = Paths.get(directoryPath, fileName);
                if (Files.exists(filePath) && Files.isRegularFile(filePath)) {
                    // Send the file size first (for client to know how much to expect)
                    try (FileInputStream fileInputStream = new FileInputStream(filePath.toFile());
                        BufferedInputStream bufferedInputStream = new BufferedInputStream(fileInputStream)) {
                        byte separator = '\t';
                        int chunkSize = 100000;
                        byte[] message = new byte[chunkSize];
                        long fileSize = Files.size(filePath);
                        // put file size
                        ByteBuffer.wrap(message).putLong(fileSize);
                        // Insert the separator after the file size
                        message[8] = separator;
                        int offset = 9;
                        bufferedInputStream.read(message, offset, message.length - offset);
                        int bytesRead = chunkSize < offset + fileSize ? chunkSize : offset + (int)fileSize;
                        do {
                            out.write(message, 0, bytesRead);
                            out.flush();
                        } while ((bytesRead = bufferedInputStream.read(message)) != -1);
                        // System.out.println("File '" + fileName + "' sent successfully.");
                    }
                } else {
                    // File doesn't exist, send an error message
                    out.write('\t');
                    out.flush();
                }
            } catch (IOException e) {
                System.err.println("Error while handling client connection: " + e.getMessage());
                e.printStackTrace();
            } finally {
                try {
                    socket.close();
                } catch (IOException e) {
                    System.out.println("Error closing client socket: " + e.getMessage());
                }
            }
        }
    }
}
