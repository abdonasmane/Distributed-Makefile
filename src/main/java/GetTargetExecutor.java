import java.io.*;
import java.net.*;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class GetTargetExecutor {

    public static Map<String, Set<String>> retrieveTargetExecutor(String serverHost, int serverPort, Set<String> dependencies) {
        Map<String, Set<String>> assocIpFiles;
        while (true) {
            try (Socket socket = new Socket()) {
                socket.connect(new InetSocketAddress(serverHost, serverPort), 500);
                socket.setSoTimeout(1000);
                ObjectInputStream in = new ObjectInputStream(socket.getInputStream());
                ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());

                Map<String, Set<String>> dataMap = new HashMap<>();
                dataMap.put("LOCATE", dependencies);

                out.writeObject(dataMap);
                out.flush();
                
                try {
                    Object o = in.readObject();
                    if (o instanceof Map) {
                        assocIpFiles = (Map<String, Set<String>>) o;
                    } else {
                        System.err.println("Received object is not of type Map.");
                        return null;
                    }
                } catch (IOException | ClassNotFoundException e) {
                    System.err.println("ERROR : Couldn't retrieve IP-FILES association");
                    return null;
                }
                // System.out.println("Received IP-FILES : " + assocIpFiles);
                break;
            } catch (SocketTimeoutException e) {
                System.err.println("Socket operation timed out while getting target Executor: " + e.getMessage() + " || retrying !");
            } catch (IOException e) {
                System.err.println("Error connecting to the server of GetTargetExecutor: " + e.getMessage());
                return null;
            }
        }
        return assocIpFiles;
    }
}
