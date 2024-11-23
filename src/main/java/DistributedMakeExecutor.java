import java.io.IOException;
import java.io.Serializable;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import org.apache.spark.api.java.JavaSparkContext;
import org.apache.spark.broadcast.Broadcast;
import org.apache.spark.api.java.JavaRDD;

import java.io.File;

public class DistributedMakeExecutor implements Serializable {
    private static final long serialVersionUID = 1L;
    private TaskGraph taskGraph;
    private Map<String, List<String>> commands;
    private Map<String, List<String>> targets;
    private String workingDirectory;
    private int serversPort;
    private int fileLocatorPort;
    private JavaSparkContext sc;

    public DistributedMakeExecutor(TaskGraph taskGraph, Map<String, List<String>> commands, Map<String, List<String>> targets, String workingDirectory, int serversPort, int fileLocatorPort, JavaSparkContext sc) {
        this.taskGraph = taskGraph;
        this.commands = commands;
        this.targets = targets;
        this.workingDirectory = workingDirectory;
        this.serversPort = serversPort;
        this.fileLocatorPort = fileLocatorPort;
        this.sc = sc;
    }

    public void execute() {
        List<List<String>> executionOrder = taskGraph.getTopologicalOrder();
        // Broadcast variables
        Broadcast<Map<String, List<String>>> broadcastCommands = sc.broadcast(commands);
        Broadcast<Map<String, List<String>>> broadcastTargets = sc.broadcast(targets);
        Broadcast<String> broadcastWorkingDirectory = sc.broadcast(workingDirectory);
        Broadcast<Integer> broadcastServerPort = sc.broadcast(serversPort);
        Broadcast<Integer> broadcastFileLocatorPort = sc.broadcast(fileLocatorPort);
        Broadcast<String> broadcastMasterIp = sc.master().contains(":") 
            ? sc.broadcast(sc.master().split(":")[1].substring(2)) 
            : sc.broadcast("localhost");

        // Iterate through execution levels
        for (int i = 0; i < executionOrder.size(); i++) {
            List<String> currentLevel = executionOrder.get(i);
            JavaRDD<String> tasksRDD = sc.parallelize(currentLevel);

            // Execute tasks at this level
            List<Boolean> taskResults = tasksRDD.map(target -> {
                try {
                    // Retrieve commands for the target
                    List<String> targetCommands = broadcastCommands.value().get(target);
                    if (targetCommands == null) {
                        System.out.println("\u001B[33mSkipping target '" + target + "' (no commands).\u001B[0m");
                        return true;
                    }

                    // Check and retrieve dependencies
                    if (!broadcastMasterIp.value().equals("localhost")) {
                        List<String> targetDependencies = broadcastTargets.value().get(target);
                        if (targetDependencies != null) {
                            for (String dependency : targetDependencies) {
                                File depFile = new File(broadcastWorkingDirectory.value() + File.separator + dependency);
                                if (!depFile.exists() || !depFile.isFile()) {
                                    String fileOwnerMachine = GetFileOwner.retrieveFileOwner(broadcastMasterIp.value(), broadcastFileLocatorPort.value(), dependency);
                                    if (fileOwnerMachine == null) {
                                        System.out.println("\t\u001B[36mGetting File: " + dependency + " from Master\u001B[0m");
                                        if (!GetFile.retrieveFile(broadcastMasterIp.value(), broadcastServerPort.value(), dependency, broadcastWorkingDirectory.value()+File.separator+dependency)) {
                                            System.err.println("\u001B[31mError: Cannot transfer file " + dependency + " for target '" + target + "' as its location is unknown.\u001B[0m");
                                            return false;
                                        }
                                        continue;
                                    }
                                    System.out.println("\t\u001B[36mGetting File: " + dependency + " from " + fileOwnerMachine + "\u001B[0m");
                                    if (!GetFile.retrieveFile(fileOwnerMachine, broadcastServerPort.value(), dependency, broadcastWorkingDirectory.value()+File.separator+dependency)) {
                                        System.err.println("\u001B[31mError: Cannot transfer file " + dependency + " for target '" + target + "' as its location is unknown.\u001B[0m");
                                        return false;
                                    }
                                }
                            }
                        }
                    }
                    // Get the initial set of files in the directory before executing commands
                    Map<String, Long> initialFiles = getFilesInDirectory(broadcastWorkingDirectory.value());

                    // Execute commands for the target
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
                            System.err.println("\t\u001B[31mCommand failed: " + command + " || Exit code : " + exitCode + "\u001B[0m");
                            return false;
                        }
                    }

                    // Get the list of files after running the commands
                    Set<String> newFiles = getNewOrModifiedFilesInDirectory(broadcastWorkingDirectory.value(), initialFiles);

                    // Print the new files that were generated
                    if (!newFiles.isEmpty()) {
                        System.out.println("\t\t\u001B[36mNew files generated:\u001B[0m");
                        for (String newFile : newFiles) {
                            System.out.println("\t\t\t\u001B[33m" + newFile + "\u001B[0m");
                        }
                    } else {
                        System.out.println("\t\t\u001B[36mNo new files generated.\u001B[0m");
                    }

                    if (!broadcastMasterIp.value().equals("localhost")) {
                        // Store file ownership for the target
                        if (!StoreFileOwner.storeFileOwner(broadcastMasterIp.value(), broadcastFileLocatorPort.value(), target)) {
                            System.err.println("\u001B[31mError: Failed to store file ownership for target '" + target + "'.\u001B[0m");
                            return false;
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
                System.err.println("\u001B[31mFatal Error: Task failure detected at level " + i + ". Exiting...\u001B[0m");
                break;
            }
        }
        // Stop SparkContext
        sc.stop();
    }

    public static Map<String, Long> getFilesInDirectory(String directoryPath) {
        Map<String, Long> fileTimestamps = new HashMap<>();
        File directory = new File(directoryPath);
        for (File file : directory.listFiles()) {
            if (file.isFile()) {
                long lastModified = file.lastModified();
                fileTimestamps.put(file.getName(), lastModified);
            }
        }
        return fileTimestamps;
    }

    public static Set<String> getNewOrModifiedFilesInDirectory(String directoryPath, Map<String, Long> initialFiles) {
        Set<String> newOrModifiedFiles = new HashSet<>();
        Map<String, Long> currentFiles = getFilesInDirectory(directoryPath);
        for (Map.Entry<String, Long> entry : currentFiles.entrySet()) {
            String fileName = entry.getKey();
            long currentTimestamp = entry.getValue();
            if (!initialFiles.containsKey(fileName) || initialFiles.get(fileName) != currentTimestamp) {
                newOrModifiedFiles.add(fileName);
            }
        }
        return newOrModifiedFiles;
    }
}

