import java.io.IOException;
import java.util.List;

import org.apache.spark.SparkConf;
import org.apache.spark.api.java.JavaRDD;
import org.apache.spark.api.java.JavaSparkContext;

import java.io.File;

public class MakeExecutor {
    private TaskGraph taskGraph;
    private String workingDirectory; // Path to the working directory

    public MakeExecutor(TaskGraph taskGraph, String workingDirectory) {
        this.taskGraph = taskGraph;
        this.workingDirectory = workingDirectory;
    }

    public void execute() {
        List<List<String>> executionOrder = taskGraph.topologicalSort();
    
        if (executionOrder == null) {
            System.out.println("\u001B[31mError: Cyclic dependencies detected. Cannot execute tasks.\u001B[0m");  // Red
            return;
        }

        // Initialize Spark context
        SparkConf conf = new SparkConf().setAppName("MakeExecutor").setMaster("local[*]");
        JavaSparkContext sc = new JavaSparkContext(conf);

        // For each level in the task graph, execute in parallel using Spark
        for (int i = 0; i < executionOrder.size(); i++) {
            List<String> currentLevel = executionOrder.get(i);

            JavaRDD<String> tasksRDD = sc.parallelize(currentLevel);

            tasksRDD.foreach(target -> {
                List<String> commands = taskGraph.getCommands().get(target);

                if (commands == null) {
                    System.out.println("\u001B[33mSkipping target '" + target + "' (no commands).\u001B[0m");  // Yellow
                    return;
                }

                System.out.println("\u001B[34mExecuting target: " + target + "\u001B[0m");  // Blue
                for (String command : commands) {
                    try {
                        executeCommand(command);
                    } catch (IOException | InterruptedException e) {
                        System.out.println("\t\u001B[31mError executing command: " + command + "\u001B[0m");  // Red
                        e.printStackTrace();
                    }
                }
            });
            tasksRDD.collect();
        }
        try {
            Thread.sleep(5000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        sc.stop();
    }    
    
    private void executeCommand(String command) throws IOException, InterruptedException {
        System.out.println("\t\u001B[36mRunning: " + command + "\u001B[0m");  // Cyan
    
        // Run the command via bash shell to handle redirection and piping
        ProcessBuilder processBuilder = new ProcessBuilder("/bin/bash", "-c", command);
    
        // Set the working directory for the command
        processBuilder.directory(new File(workingDirectory));
        processBuilder.inheritIO();  // Redirect output to the console for better visibility
    
        Process process = processBuilder.start();
        int exitCode = process.waitFor();
        if (exitCode == 0) {
            System.out.println("\t\u001B[32mCommand succeeded: " + command + "\u001B[0m");  // Green
        } else {
            System.out.println("\t\u001B[31mCommand failed: " + command + "\u001B[0m");  // Red
            throw new IOException("\t\u001B[31mCommand failed with exit code: " + exitCode + "\u001B[0m");  // Red
        }
    }            
}
