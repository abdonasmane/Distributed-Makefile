import java.io.*;
import java.net.*;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class FileLocatorServer {
    // A map to store file names and the IP addresses of machines that have them
    // assoc target-IP
    private static final ConcurrentHashMap<String, String> assocTargetOwner = new ConcurrentHashMap<>();
    // assoc taget-generated files
    private static final ConcurrentHashMap<String, Set<String>> assocTargetFiles = new ConcurrentHashMap<>();
    private static int PORT = 5000;
    private static String initialFilesDirectory;
    private static final String commonInitialTarget = "intitial_" + UUID.randomUUID();
    private static final ExecutorService threadPool = Executors.newFixedThreadPool(200);

    public static void main(String[] args) {
        if (args.length == 2) {
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
            System.out.println("Usage: java FileLocatorServer <SERVER_PORT> <INITIAL_FILES_DIRECTORY>");
            return;
        }

        System.out.println("File Locator Server is running on port " + PORT + "...");
        initialFilesDirectory = args[1];

        Map<String, Long> initialFiles = FileDetector.getFilesInDirectory(initialFilesDirectory);
        // handle the case where the dependency is a file
        try {
            for (String file : initialFiles.keySet()) {
                assocTargetOwner.put(file, InetAddress.getLocalHost().getHostAddress());
                Set<String> dummy = new HashSet<>();
                dummy.add(file);
                assocTargetFiles.put(file, dummy);
            }
            assocTargetFiles.put(commonInitialTarget, new HashSet<>(initialFiles.keySet()));
        } catch (UnknownHostException u) {
            System.err.println("Couldn't find masterIp to store initial Files");
        }

        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            while (true) {
                // Accept an incoming connection
                Socket clientSocket = serverSocket.accept();
                // Handle the client in a separate thread
                threadPool.submit(new ClientHandler(clientSocket));
                // new Thread(new ClientHandler(clientSocket)).start();
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
                ObjectOutputStream out = new ObjectOutputStream(clientSocket.getOutputStream());
                ObjectInputStream in = new ObjectInputStream(clientSocket.getInputStream());
            ) {
                Map<String, Set<String>> receivedRequest;
                try {
                    Object o = in.readObject();
                    if (o instanceof Map) {
                        receivedRequest = (Map<String, Set<String>>) o;
                    } else {
                        System.out.println("Received object is not of type Map.");
                        return;
                    }
                } catch (IOException | ClassNotFoundException e) {
                    System.out.println("Error handling client: " + e.getMessage());
                    return;
                }
                if (receivedRequest.containsKey("LOCATE")) {
                    handleLocateRequest(out, receivedRequest);
                } else {
                    handleStoreRequest(out, receivedRequest);
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

        private void handleStoreRequest(ObjectOutputStream out, Map<String, Set<String>> receivedRequest) throws IOException {
            // System.out.println("New connection from " + clientSocket.getInetAddress() + " to STORE " + receivedRequest);
            String clientIP = clientSocket.getInetAddress().getHostAddress();
            assocTargetOwner.put(receivedRequest.keySet().iterator().next(), clientIP);
            assocTargetFiles.putAll(receivedRequest);
            if (!clientIP.equals(InetAddress.getLocalHost().getHostAddress())) {
                // System.out.println("Bringing files " + receivedRequest.get(receivedRequest.keySet().iterator().next()) + " from " + clientIP + " for target " + receivedRequest.keySet().iterator().next());
                for (String fileName : receivedRequest.get(receivedRequest.keySet().iterator().next())) {
                    File fileToCheck = new File(initialFilesDirectory+File.separator+fileName);
                    if (fileToCheck.exists()) {
                        // System.out.println("Skipping file " + fileName + " because It exists already");
                        continue;
                    }
                    GetFile.retrieveFile(clientIP, 8888, fileName, initialFilesDirectory+File.separator+fileName);
                }
            }
            out.writeObject(1);
            out.flush();
        }

        private void handleLocateRequest(ObjectOutputStream out, Map<String, Set<String>> receivedRequest) throws IOException {
            // Read the file name
            // System.out.println("New connection from " + clientSocket.getInetAddress() + " to LOCATE " + receivedRequest);
            Map<String, Set<String>> assocIpFiles = new HashMap<>();
            for (String targetName: receivedRequest.get("LOCATE")) {
                if (!assocTargetOwner.containsKey(targetName)) {
                    continue;
                }
                String targetOwnerIP = new String(assocTargetOwner.get(targetName));
                if (assocIpFiles.containsKey(targetOwnerIP)) {
                    assocIpFiles.get(targetOwnerIP).addAll(new HashSet<>(assocTargetFiles.get(targetName)));
                } else {
                    assocIpFiles.put(targetOwnerIP, new HashSet<>(assocTargetFiles.get(targetName)));
                }
            }
            assocIpFiles.merge(
                InetAddress.getLocalHost().getHostAddress(),
                new HashSet<>(assocTargetFiles.get(commonInitialTarget)),
                (oldSet, newSet) -> {
                    oldSet.addAll(newSet);
                    return oldSet;
                }
            );
            out.writeObject(assocIpFiles);
            out.flush();
        }
    }
}
