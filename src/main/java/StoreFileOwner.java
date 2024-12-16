import java.io.*;
import java.net.*;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class StoreFileOwner {

    public static boolean storeFileOwner(String serverHost, int serverPort, String targetName, Set<String> generatedFiles) {
        int number_of_tries = 5;
        while (number_of_tries > 0) {
            try (Socket socket = new Socket()) {
                socket.connect(new InetSocketAddress(serverHost, serverPort), 500);
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
            } catch (IOException e) {
                System.err.println("Error connecting to the server or StoreFileOwner: " + e.getMessage());
                return false;
            }
            number_of_tries--;
        }
        return false;
    }
}
