import java.io.*;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.PosixFilePermissions;

public class GetFile {

    public static boolean retrieveFile(String serverHost, int serverPort, String fileName, String destinationPath) {
        int number_of_tries = 5;
        while (number_of_tries > 0) {
            try (Socket socket = new Socket()) {
                socket.connect(new InetSocketAddress(serverHost, serverPort), 500);
                InputStream in = socket.getInputStream();
                OutputStream out = socket.getOutputStream();
                // System.out.println("Requesting file " + fileName + " from " + serverHost + " by " + InetAddress.getLocalHost().getHostAddress());

                out.write((fileName + "\n").getBytes());
                out.flush();

                // Receive the file size first
                byte[] sizeBuffer = new byte[8];
                in.read(sizeBuffer);
                if (sizeBuffer[0] == '\t') {
                    System.err.println("Error : couldn't retrieve file from the server " + serverHost);
                    return false;
                }
                long fileSize = ByteBuffer.wrap(sizeBuffer).getLong();

                // Consume separator (tab byte)
                in.read(); 

                // Prepare to receive the file
                byte[] buffer = new byte[100000];
                try (FileOutputStream fileOutputStream = new FileOutputStream(destinationPath)) {
                    int bytesRead;
                    long totalBytesRead = 0;

                    // Read the file in chunks and write to the local file
                    while (totalBytesRead < fileSize) {
                        bytesRead = in.read(buffer);
                        totalBytesRead += bytesRead;
                        fileOutputStream.write(buffer, 0, bytesRead);
                    }
                    Path path = Paths.get(destinationPath);
                    Files.setPosixFilePermissions(path, PosixFilePermissions.fromString("rwxrwxrwx"));
                }

                // System.out.println("File '" + fileName + "' received successfully.");
                return true;
            } catch (SocketTimeoutException e) {
                System.err.println("Socket operation timed out while getting file : " + e.getMessage() + " || Retrying !");
            } catch (IOException e) {
                System.err.println("Error connecting to the server of GetFile: " + e.getMessage());
                return false;
            }
            number_of_tries --;
        }
        return false;
    }
}
