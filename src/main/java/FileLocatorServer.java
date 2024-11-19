import java.io.*;
import java.net.*;
import java.util.concurrent.ConcurrentHashMap;

public class FileLocatorServer {
    // A map to store file names and the IP addresses of machines that have them
    private static final ConcurrentHashMap<String, String> fileRegistry = new ConcurrentHashMap<>();
    private static int PORT = 5000;

    public static void main(String[] args) {
        if (args.length == 1) {
            try {
                PORT = Integer.parseInt(args[0]);
                if (PORT < 1 || PORT > 65535) {
                    throw new IllegalArgumentException("ERROR: SERVER_PORT must be between 1 and 65535.");
                }
            } catch (NumberFormatException e) {
                PORT = 5000;
                System.err.println("Invalid port number. Using default port " + PORT);
            }
        } else {
            System.out.println("Usage: java FileLocatorServer <SERVER_PORT>");
            return;
        }

        System.out.println("File Locator Server is running on port " + PORT + "...");

        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            while (true) {
                // Accept an incoming connection
                Socket clientSocket = serverSocket.accept();
                System.out.println("New connection from " + clientSocket.getInetAddress());

                // Handle the client in a separate thread
                new Thread(new ClientHandler(clientSocket)).start();
            }
        } catch (IOException e) {
            System.err.println("Error in server: " + e.getMessage());
        }
    }

    // Inner class to handle client connections
    private static class ClientHandler implements Runnable {
        private final Socket clientSocket;

        public ClientHandler(Socket clientSocket) {
            this.clientSocket = clientSocket;
        }

        @Override
        public void run() {
            try (
                InputStream in = clientSocket.getInputStream();
                OutputStream out = clientSocket.getOutputStream();
            ) {
                // Read the request type
                byte requestType = (byte) in.read();
                if (requestType == '\t') {
                    handleStoreRequest(in, out);
                } else if (requestType == '\n') {
                    handleLocateRequest(in, out);
                } else {
                    System.out.println("Error while handling client connection: ERROR: Unknown request type");
                    out.write(0);
                    return;
                }
            } catch (IOException e) {
                System.out.println("Error handling client: " + e.getMessage());
                return;
            } finally {
                try {
                    clientSocket.close();
                } catch (IOException e) {
                    System.out.println("Error closing client socket: " + e.getMessage());
                }
            }
        }

        private void handleStoreRequest(InputStream in, OutputStream out) throws IOException {
            // Read the file name
            byte[] fileNameBytes = new byte[256];
            int index = 0;
            byte r;
            while ((r = (byte) in.read()) != '\n' && r != -1) {
                fileNameBytes[index] = r;
                index++;
            }
            String fileName = new String(fileNameBytes, 0, index);
            String clientIP = clientSocket.getInetAddress().getHostAddress();
            fileRegistry.put(fileName, clientIP);
            System.out.println("Stored file: " + fileName + " from " + clientIP);
            out.write(1);
        }

        private void handleLocateRequest(InputStream in, OutputStream out) throws IOException {
            // Read the file name
            byte[] fileNameBytes = new byte[256];
            int index = 0;
            byte r;
            while ((r = (byte) in.read()) != '\n' && r != -1) {
                fileNameBytes[index] = r;
                index++;
            }
            String fileName = new String(fileNameBytes, 0, index);
            String fileOwnerIP = fileRegistry.get(fileName);
            if (fileOwnerIP != null) {
                out.write((fileOwnerIP + "\n").getBytes());
            } else {
                out.write('\n');
            }
        }
    }
}
