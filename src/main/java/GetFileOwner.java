import java.io.*;
import java.net.*;

public class GetFileOwner {

    public static String retrieveFileOwner(String serverHost, int serverPort, String fileName) {
        try (Socket socket = new Socket(serverHost, serverPort)) {
            InputStream in = socket.getInputStream();
            OutputStream out = socket.getOutputStream();

            out.write(("\n" + fileName + "\n").getBytes());
            out.flush();

            // must be changed...many senarios will f this up
            // Receive the ipAdress size first
            byte[] fileOwner = new byte[64];
            int bytesRead = in.read(fileOwner);
            if (fileOwner[0] == '\n') {
                System.err.println("Error : couldn't retrieve file " + fileName + " from server " + serverHost);
                return null;
            }
            String fileOwnerIp = new String(fileOwner, 0, bytesRead - 1);
            System.out.println("File '" + fileName + "' located at " + fileOwnerIp);
            return fileOwnerIp;
        } catch (IOException e) {
            System.err.println("Error connecting to the server: " + e.getMessage());
            return null;
        }
    }
}
