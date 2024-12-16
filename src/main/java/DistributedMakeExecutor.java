import java.io.Serializable;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.apache.spark.api.java.JavaSparkContext;
import org.apache.spark.broadcast.Broadcast;
import org.apache.spark.api.java.JavaRDD;
import java.io.File;
import java.io.IOException;

public class DistributedMakeExecutor implements Serializable {
    private static final long serialVersionUID = 1L;
    private TaskGraph taskGraph;
    private Map<String, List<String>> commands;
    private Map<String, Set<String>> dependenciesTree;
    private String workingDirectory;
    private int serversPort;
    private int fileLocatorPort;
    private JavaSparkContext sc;

    public DistributedMakeExecutor(TaskGraph taskGraph, Map<String, List<String>> commands, String workingDirectory, int serversPort, int fileLocatorPort, JavaSparkContext sc) {
        this.taskGraph = taskGraph;
        this.dependenciesTree = taskGraph.getDependenciesTree();
        this.commands = commands;
        this.workingDirectory = workingDirectory;
        this.serversPort = serversPort;
        this.fileLocatorPort = fileLocatorPort;
        this.sc = sc;
    }

    public void execute() {
        boolean shouldExecuteLastLevel = true;
        List<List<String>> executionOrder = taskGraph.getTopologicalOrder();
        // Broadcast variables
        Broadcast<Map<String, List<String>>> broadcastCommands = sc.broadcast(commands);
        Broadcast<Map<String, Set<String>>> broadcastDependenciesTree = sc.broadcast(dependenciesTree);
        Broadcast<String> broadcastWorkingDirectory = sc.broadcast(workingDirectory);
        Broadcast<Integer> broadcastServerPort = sc.broadcast(serversPort);
        Broadcast<Integer> broadcastFileLocatorPort = sc.broadcast(fileLocatorPort);
        Broadcast<String> broadcastMasterIp = sc.master().contains(":") 
            ? sc.broadcast(sc.master().split(":")[1].substring(2)) 
            : sc.broadcast("localhost");

        // Iterate through execution levels
        for (int i = 0; i < executionOrder.size() - 1; i++) {
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
                    String tempDirName = "temp_" + target + "_" + UUID.randomUUID();
                    String tempDirPath = broadcastWorkingDirectory.value() + File.separator + tempDirName;
                    File tempDir = new File(tempDirPath);

                    // Step 1: Create and copy files to temporary directory
                    if (!tempDir.mkdir()) {
                        throw new IOException("Failed to create temporary directory: " + tempDirPath);
                    }
                    // FileUtils.copyDirectory(new File(broadcastWorkingDirectory.value()), tempDir, pathname -> {
                    //     return !pathname.getName().startsWith("temp_");
                    // });

                    // // Get the initial set of files in the directory before executing commands
                    // Map<String, Long> initialFiles = FileDetector.getFilesInDirectory(tempDirPath);
                    Set<String> broughtFiles = new HashSet<>();

                    // Check and retrieve dependencies
                    // direct dependencie
                    // List<String> directTargetDependencies = broadcastTargets.value().get(target);
                    Set<String> allTargetDependencies = broadcastDependenciesTree.value().getOrDefault(target, null);
                    if (allTargetDependencies != null) {
                        Map<String, Set<String>> assocIpFiles = GetTargetExecutor.retrieveTargetExecutor(broadcastMasterIp.value(), broadcastFileLocatorPort.value(), allTargetDependencies);
                        // initial stages
                        for (String machineIp : assocIpFiles.keySet()) {
                            Set<String> files = assocIpFiles.get(machineIp);
                            for (String file : files) {
                                File fileToCheck = new File(broadcastWorkingDirectory.value() + File.separator + file);
                                if (!fileToCheck.exists()) {
                                    if (!GetFile.retrieveFile(machineIp, broadcastServerPort.value(), file, tempDirPath + File.separator + file)) {
                                        System.err.println("\u001B[31mError: Cannot transfer file " + file + " as its location is unknown.\u001B[0m");
                                        return false;
                                    }
                                    broughtFiles.add(file);
                                } else {
                                    File destFile = new File(tempDirPath + File.separator + file);
                                    try {
                                        Files.copy(fileToCheck.toPath(), destFile.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
                                    } catch (IOException e) {
                                        System.err.println("\u001B[31mError copying file " + fileToCheck + " to " + destFile + ": " + e.getMessage() + "\u001B[0m");
                                        return false;
                                    }
                                }
                            }
                        }
                    }

                    // Get the initial set of files in the directory before executing commands
                    Map<String, Long> initialFiles = FileDetector.getFilesInDirectory(tempDirPath);

                    // Execute commands for the target
                    // System.out.println("\u001B[34mExecuting target: " + target + "\u001B[0m");
                    // for (String command : targetCommands) {
                    //     // System.out.println("\t\u001B[36mRunning: " + command + "\u001B[0m");
                    //     ProcessBuilder processBuilder = new ProcessBuilder("/bin/bash", "-c", command);
                    //     processBuilder.directory(tempDir);
                    //     processBuilder.inheritIO();
                    //     Process process = processBuilder.start();
                    //     int exitCode = process.waitFor();
                    //     if (exitCode != 0) {
                    //         // System.out.println("\t\u001B[32mCommand succeeded: " + command + "\u001B[0m");
                    //     // } else {
                    //         System.err.println("\t\u001B[31mCommand failed: " + command + " || Exit code : " + exitCode + "\u001B[0m");
                    //         return false;
                    //     }
                    // }
                    for (String command : targetCommands) {
                        int num_of_tries = 3;
                        while (true) {
                            // System.out.println("\t\u001B[36mRunning: " + command + "\u001B[0m");
                            ProcessBuilder processBuilder = new ProcessBuilder("/bin/bash", "-c", command);
                            processBuilder.directory(tempDir);
                            processBuilder.inheritIO();
                            Process process = processBuilder.start();
                            int exitCode = process.waitFor();
                            if (exitCode == 0) {
                                break;
                                // System.out.println("\t\u001B[32mCommand succeeded: " + command + "\u001B[0m");
                            // } else {
                                // System.err.println("\t\u001B[31mCommand failed: " + command + " || Exit code : " + exitCode + "\u001B[0m");
                                // return false;
                            } else {
                                num_of_tries--;
                                if (num_of_tries < 0) {
                                    System.err.println("\t\u001B[31mCommand failed: " + command + " || Exit code : " + exitCode + "\u001B[0m");
                                    return false;
                                }
                            }
                        }
        
                    }
                    // Get the list of files after running the commands
                    Set<String> newFiles = FileDetector.getNewOrModifiedFilesInDirectory(tempDirPath, initialFiles);
                    
                    // Move new files to the original directory
                    for (String newFile : newFiles) {
                        File sourceFile = new File(tempDirPath + File.separator + newFile);
                        File destFile = new File(broadcastWorkingDirectory.value() + File.separator + newFile);
                        if (!destFile.exists()) {
                            try {
                                Files.move(sourceFile.toPath(), destFile.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                            } catch (IOException e) {
                                System.err.println("\u001B[31mError moving file " + sourceFile + " to " + destFile + ": " + e.getMessage() + "\u001B[0m");
                                return false;
                            }
                        }
                    }

                    for (String broughtFile : broughtFiles) {
                        File sourceFile = new File(tempDirPath + File.separator + broughtFile);
                        File destFile = new File(broadcastWorkingDirectory.value() + File.separator + broughtFile);
                        if (!destFile.exists()) {
                            try {
                                Files.move(sourceFile.toPath(), destFile.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                            } catch (IOException e) {
                                System.err.println("\u001B[31mError moving file " + sourceFile + " to " + destFile + ": " + e.getMessage() + "\u001B[0m");
                                return false;
                            }
                        }
                    }

                    // Print the new files that were generated
                    if (!newFiles.isEmpty()) {
                        // System.out.println("\t\t\u001B[36mNew files generated:\u001B[0m");
                        // for (String newFile : newFiles) {
                        //     System.out.println("\t\t\t\u001B[33m" + newFile + "\u001B[0m");
                        // }
                        // Store file ownership for the target
                        if (!StoreFileOwner.storeFileOwner(broadcastMasterIp.value(), broadcastFileLocatorPort.value(), target, newFiles)) {
                            System.err.println("\u001B[31mError: Failed to store file ownership for target '" + target + "'.\u001B[0m");
                            return false;
                        }
                    }
                    // } else {
                    //     System.out.println("\t\t\u001B[36mNo new files generated.\u001B[0m");
                    // }
                    // FileUtils.deleteDirectory(tempDir);
                    ProcessBuilder processBuilder = new ProcessBuilder("/bin/bash", "-c", "rm -rf " + tempDirName);
                    processBuilder.directory(new File(broadcastWorkingDirectory.value()));
                    processBuilder.inheritIO();
                    processBuilder.start();
                    // Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                    //     try {
                    //         FileUtils.deleteDirectory(tempDir);
                    //     } catch (IOException e) {
                    //         e.printStackTrace();
                    //     }
                    // }));
                    return true;
                } catch (Exception e) {
                    System.err.println("\u001B[31mTask failed: " + target + " || Error: " + e.getMessage() + "\u001B[0m");
                    return false;
                }
            }).collect();

            // Check for failures
            if (taskResults.contains(false)) {
                System.err.println("\u001B[31mFatal Error: Task failure detected at level " + (i+1) + ". Exiting...\u001B[0m");
                shouldExecuteLastLevel = false;
                break;
            }
        }
        if (shouldExecuteLastLevel) {
            String lastLevel = executionOrder.get(executionOrder.size()-1).get(0);
            List<String> targetCommands = commands.get(lastLevel);
            if (targetCommands == null) {
                System.out.println("\u001B[33mSkipping last target '" + lastLevel + "' (no commands).\u001B[0m");
            } else {
                // Get the initial set of files in the directory before executing commands
                // Map<String, Long> initialFiles = FileDetector.getFilesInDirectory(workingDirectory);

                // Execute commands for the target
                // System.out.println("\u001B[34mExecuting last level target: " + lastLevel + "\u001B[0m");
                try {
                    for (String command : targetCommands) {
                        // System.out.println("\t\u001B[36mRunning: " + command + "\u001B[0m");
                        ProcessBuilder processBuilder = new ProcessBuilder("/bin/bash", "-c", command);
                        processBuilder.directory(new File(workingDirectory));
                        processBuilder.inheritIO();

                        Process process = processBuilder.start();
                        int exitCode = process.waitFor();
                        if (exitCode != 0) {
                        //     System.out.println("\t\u001B[32mCommand succeeded: " + command + "\u001B[0m");
                        // } else {
                            System.err.println("\t\u001B[31mCommand failed: " + command + " || Exit code : " + exitCode + "\u001B[0m");
                        }
                    }
                } catch (Exception e) {
                    System.err.println("\u001B[31mTask failed: " + lastLevel + " || Error: " + e.getMessage() + "\u001B[0m");
                }
                // Get the list of files after running the commands
                // Set<String> newFiles = FileDetector.getNewOrModifiedFilesInDirectory(workingDirectory, initialFiles);
                // Print the new files that were generated
                // if (!newFiles.isEmpty()) {
                //     System.out.println("\t\t\u001B[36mNew files generated:\u001B[0m");
                //     for (String newFile : newFiles) {
                //         System.out.println("\t\t\t\u001B[33m" + newFile + "\u001B[0m");
                //     }
                // } else {
                //     System.out.println("\t\t\u001B[36mNo new files generated.\u001B[0m");
                // }
            }
        }
        // Stop SparkContext
        sc.stop();
    }
}

