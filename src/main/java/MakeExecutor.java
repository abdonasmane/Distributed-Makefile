import java.io.IOException;
import java.io.Serializable;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.spark.api.java.JavaRDD;
import org.apache.spark.api.java.JavaSparkContext;
import org.apache.spark.broadcast.Broadcast;

import java.io.File;

public class MakeExecutor implements Serializable {
    private static final long serialVersionUID = 1L;
    private TaskGraph taskGraph;
    private Map<String, List<String>> commands;
    private String workingDirectory; // Path to the working directory
    private JavaSparkContext sc;

    public MakeExecutor(TaskGraph taskGraph, Map<String, List<String>> commands, String workingDirectory, JavaSparkContext sc) {
        this.taskGraph = taskGraph;
        this.commands = commands;
        this.workingDirectory = workingDirectory;
        this.sc = sc;
    }

    public void execute() {
        List<List<String>> executionOrder = taskGraph.getTopologicalOrder();
        Broadcast<Map<String, List<String>>> broadcastCommands = sc.broadcast(commands);
        Broadcast<String> broadcastWorkingDirectory = sc.broadcast(workingDirectory);

        if (executionOrder == null) {
            System.out.println("\u001B[31mError: Cyclic dependencies detected. Cannot execute tasks.\u001B[0m");  // Red
            return;
        }

        // For each level in the task graph, execute in parallel using Spark
        for (int i = 0; i < executionOrder.size(); i++) {
            List<String> currentLevel = executionOrder.get(i);
            JavaRDD<String> tasksRDD = sc.parallelize(currentLevel);

            // Collect the results of each task execution
            List<Boolean> taskResults = tasksRDD.map(target -> {
                try {
                    List<String> targetCommands = broadcastCommands.value().get(target);

                    if (targetCommands == null) {
                        System.out.println("\u001B[33mSkipping target '" + target + "' (no commands).\u001B[0m");
                        return true;
                    }

                    System.out.println("\u001B[34mExecuting target: " + target + "\u001B[0m");
                    for (String command : targetCommands) {
                        System.out.println("\t\u001B[36mRunning: " + command + "\u001B[0m");
                        ProcessBuilder processBuilder = new ProcessBuilder("/bin/bash", "-c", command);
                        processBuilder.directory(new File(broadcastWorkingDirectory.value()));
                        processBuilder.inheritIO();

                        Process process = processBuilder.start();
                        int exitCode = process.waitFor();
                        if (exitCode == 0) {
                            System.out.println("\t\u001B[32mCommand succeeded: " + command + "\u001B[0m");
                        } else {
                            throw new IOException("\t\u001B[31mCommand failed: " + command + " || Exit code : " + exitCode + "\u001B[0m");
                        }
                    }
                    return true;

                } catch (IOException | InterruptedException e) {
                    System.out.println(e.getMessage());
                    return false;
                }
            }).collect();

            if (taskResults.contains(false)) {
                System.out.println("\u001B[31mFatal Error: Exiting make due to task failure at level " + i + "\u001B[0m");
                break;
            }
        }

        sc.stop();
    }
}
