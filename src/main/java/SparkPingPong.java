import org.apache.spark.SparkConf;
import org.apache.spark.api.java.JavaRDD;
import org.apache.spark.api.java.JavaSparkContext;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class SparkPingPong {

    private static final String PING_FILE = "ping_pong_responses.txt";

    public static void main(String[] args) {
        // Configuration and Spark context initialization
        SparkConf conf = new SparkConf().setAppName("File-Based Ping-Pong").setMaster("local[*]");
        JavaSparkContext sc = new JavaSparkContext(conf);

        // Clean up any existing files
        try {
            Files.deleteIfExists(Paths.get(PING_FILE));
            // Create new empty file
            Files.createFile(Paths.get(PING_FILE));

            // Define the number of ping-pong messages
            final int numMessages = 10; // number of ping-pong messages

            // Prepare the initial data for ping-pong
            List<Integer> data = new ArrayList<>();
            for (int i = 0; i < numMessages; i++) {
                data.add(i); // Each task will process an integer
            }

            // Distribute the data across the cluster
            JavaRDD<Integer> distributedData = sc.parallelize(data);

            // Execute the ping-pong
            distributedData.foreach(message -> {
                // Send a ping (write to the ping file)
                sendPing(message);
                // Wait and then read a pong (read from the pong file)
                String pongResponse = receivePong();
                System.out.println("Task " + message + ": Ping sent, Pong received: \n" + pongResponse);
            });
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            // Ensure the Spark context is stopped and the file is deleted
            sc.close();
            try {
                Files.deleteIfExists(Paths.get(PING_FILE));
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    // Method to send a ping by writing to the ping file
    private static void sendPing(int message) {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(PING_FILE, true))) {
            writer.write("Ping from task " + message + "\n");
            // writer.flush(); // Optional: Not necessary since BufferedWriter flushes on close
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // Method to receive a pong by reading from the ping file
    private static String receivePong() {
        StringBuilder pongResponse = new StringBuilder();
        try {
            List<String> lines = Files.readAllLines(Paths.get(PING_FILE));
            for (String line : lines) {
                pongResponse.append(line).append("\n");
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return pongResponse.toString().trim();
    }
}
