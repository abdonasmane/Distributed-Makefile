import java.io.*;
import java.net.*;

public class StoreFileOwner {

    public static boolean storeFileOwner(String serverHost, int serverPort, String fileName) {
        try (Socket socket = new Socket(serverHost, serverPort)) {
            InputStream in = socket.getInputStream();
            OutputStream out = socket.getOutputStream();

            out.write(("\t" + fileName + "\n").getBytes());
            out.flush();

            in.read();
            return true;
        } catch (IOException e) {
            System.err.println("Error connecting to the server: " + e.getMessage());
            return false;
        }
    }
}
