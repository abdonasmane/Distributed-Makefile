import java.io.*;
import java.net.*;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class StoreFileOwner {

    public static boolean storeFileOwner(String serverHost, int serverPort, String targetName, Set<String> generatedFiles) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(serverHost, serverPort), 0);
            // socket.setSoTimeout(5000);
            ObjectInputStream in = new ObjectInputStream(socket.getInputStream());
            ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());

            Map<String, Set<String>> dataMap = new HashMap<>();
            dataMap.put(targetName, generatedFiles);

            out.writeObject(dataMap);
            out.flush();
            try {
                in.readObject();
            } catch (IOException | ClassNotFoundException e) {
                System.out.println("Error handling Server ACK: " + e.getMessage());
                return false;
            }
            return true;
        } catch (SocketTimeoutException e) {
            System.err.println("Socket operation timed out while storing file owner: " + e.getMessage());
            return false;
        } catch (IOException e) {
            System.err.println("Error connecting to the server or StoreFileOwner: " + e.getMessage());
            return false;
        }
    }
}
