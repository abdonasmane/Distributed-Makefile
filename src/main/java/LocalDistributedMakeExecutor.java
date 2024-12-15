import java.io.Serializable;
import java.util.List;
import java.util.Map;

import org.apache.spark.api.java.JavaSparkContext;
import org.apache.spark.broadcast.Broadcast;
import org.apache.spark.api.java.JavaRDD;

import java.io.File;

public class LocalDistributedMakeExecutor implements Serializable {
    private static final long serialVersionUID = 1L;
    private TaskGraph taskGraph;
    private Map<String, List<String>> commands;
    private String workingDirectory;
    private JavaSparkContext sc;

    public LocalDistributedMakeExecutor(TaskGraph taskGraph, Map<String, List<String>> commands, String workingDirectory, JavaSparkContext sc) {
        this.taskGraph = taskGraph;
        this.commands = commands;
        this.workingDirectory = workingDirectory;
        this.sc = sc;
    }

    public void execute() {
        List<List<String>> executionOrder = taskGraph.getTopologicalOrder();
        // Broadcast variables
        Broadcast<Map<String, List<String>>> broadcastCommands = sc.broadcast(commands);
        Broadcast<String> broadcastWorkingDirectory = sc.broadcast(workingDirectory);

        // Iterate through execution levels
        for (int i = 0; i < executionOrder.size(); i++) {
            List<String> currentLevel = executionOrder.get(i);
            JavaRDD<String> tasksRDD = sc.parallelize(currentLevel, currentLevel.size());

            // Execute tasks at this level
            List<Boolean> taskResults = tasksRDD.map(target -> {
                try {
                    // Retrieve commands for the target
                    List<String> targetCommands = broadcastCommands.value().get(target);
                    if (targetCommands == null) {
                        // System.out.println("\u001B[33mSkipping target '" + target + "' (no commands).\u001B[0m");
                        return true;
                    }

                    // Execute commands for the target
                    // System.out.println("\u001B[34mExecuting target: " + target + "\u001B[0m");
                    for (String command : targetCommands) {
                        int num_of_tries = 3;
                        while (true) {
                            // System.out.println("\t\u001B[36mRunning: " + command + "\u001B[0m");
                            ProcessBuilder processBuilder = new ProcessBuilder("/bin/bash", "-c", command);
                            processBuilder.directory(new File(broadcastWorkingDirectory.value()));
                            processBuilder.inheritIO();
                            Process process = processBuilder.start();
                            int exitCode = process.waitFor();
                            if (exitCode == 0) {
                                break;
                            } else {
                                num_of_tries--;
                                if (num_of_tries < 0) {
                                    System.err.println("\t\u001B[31mCommand failed: " + command + " || Exit code : " + exitCode + "\u001B[0m");
                                    return false;
                                }
                            }
                        }
        
                    }
                    return true;
                } catch (Exception e) {
                    System.err.println("\u001B[31mTask failed: " + target + " || Error: " + e.getMessage() + "\u001B[0m");
                    return false;
                }
            }).collect();

            // Check for failures
            if (taskResults.contains(false)) {
                System.err.println("\u001B[31mFatal Error: Task failure detected at level " + (i+1) + ". Exiting...\u001B[0m");
                break;
            }
        }
        // Stop SparkContext
        sc.stop();
    }
}

