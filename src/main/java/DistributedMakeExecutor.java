import java.io.IOException;
import java.io.Serializable;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.spark.api.java.JavaRDD;
import org.apache.spark.api.java.JavaSparkContext;
import org.apache.spark.broadcast.Broadcast;

import java.io.File;

public class DistributedMakeExecutor implements Serializable {
    private static final long serialVersionUID = 1L;
    private TaskGraph taskGraph;
    private Map<String, List<String>> commands;
    private Map<String, List<String>> targets;
    private String workingDirectory;
    private int serversPort;
    private int fileLocalotPort;
    private JavaSparkContext sc;

    public DistributedMakeExecutor(TaskGraph taskGraph, Map<String, List<String>> commands, Map<String, List<String>> targets, String workingDirectory, int serversPort, int fileLocalotPort, JavaSparkContext sc) {
        this.taskGraph = taskGraph;
        this.commands = commands;
        this.targets = targets;
        this.workingDirectory = workingDirectory;
        this.serversPort = serversPort;
        this.fileLocalotPort = fileLocalotPort;
        this.sc = sc;
    }

    public void execute() {
        List<List<String>> executionOrder = taskGraph.getTopologicalOrder();
        Broadcast<Map<String, List<String>>> broadcastCommands = sc.broadcast(commands);
        Broadcast<Map<String, List<String>>> broadcastTargets = sc.broadcast(targets);
        Broadcast<String> broadcastWorkingDirectory = sc.broadcast(workingDirectory);
        Broadcast<Integer> broadcastServerPort = sc.broadcast(serversPort);
        Broadcast<Integer> broadcastFileLocatorPort = sc.broadcast(fileLocalotPort);
        String dummy = "localhost";
        Broadcast<String> broadcastMasterIp = sc.master().contains(":") ? sc.broadcast(sc.master().split(":")[1].substring(2)) : sc.broadcast(dummy);
    
        if (executionOrder == null) {
            System.out.println("\u001B[31mError: Cyclic dependencies detected. Cannot execute tasks.\u001B[0m");
            return;
        }

        // For each level in the task graph, execute in parallel using Spark
        for (int i = 0; i < executionOrder.size(); i++) {
            List<String> currentLevel = executionOrder.get(i);
            JavaRDD<String> tasksRDD = sc.parallelize(currentLevel);

            // Collect the results of each task execution
            List<Boolean> generatedTargets = tasksRDD.map(target -> {
                try {
                    List<String> targetCommands = broadcastCommands.value().get(target);

                    if (targetCommands == null) {
                        System.out.println("\u001B[33mSkipping target '" + target + "' (no commands).\u001B[0m");
                        return true;
                    }

                    List<String> targetDependencies = broadcastTargets.value().get(target);
                    if (targetDependencies != null && !targetDependencies.isEmpty()) {
                        for (String dependency : targetDependencies) {
                            File file = new File(broadcastWorkingDirectory.value() + File.separator + dependency);
                            if (!file.exists() || !file.isFile()) {
                                String fileOwnerMachine = GetFileOwner.retrieveFileOwner(broadcastMasterIp.value(), broadcastFileLocatorPort.value(), dependency);
                                if (fileOwnerMachine == null) {
                                    if (!GetFile.retrieveFile(broadcastMasterIp.value(), broadcastServerPort.value(), dependency, broadcastWorkingDirectory.value()+File.separator+dependency)) {
                                        System.out.println("\u001B[31mError: Cannot transfer file " + dependency + " for target '" + target + "' as its location is unknown.\u001B[0m");
                                        return false;
                                    }
                                    continue;
                                }
                                System.out.println("\t\u001B[36mGetting File: " + dependency + " from " + fileOwnerMachine + "\u001B[0m");
                                if (!GetFile.retrieveFile(fileOwnerMachine, broadcastServerPort.value(), dependency, broadcastWorkingDirectory.value()+File.separator+dependency)) {
                                    System.out.println("\u001B[31mError: Cannot transfer file " + dependency + " for target '" + target + "' as its location is unknown.\u001B[0m");
                                    return false;
                                }
                            }
                        }
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
                    try {
                        String machineIp = InetAddress.getLocalHost().getHostAddress();
                        if (!StoreFileOwner.storeFileOwner(broadcastMasterIp.value(), broadcastFileLocatorPort.value(), target)) {
                            System.out.println("\u001B[31mCouldn't Store dependency " + target + " by machine " + machineIp + "\u001B[0m");
                            return false;
                        }
                        return true;
                    } catch (UnknownHostException u) {
                        System.out.println("Network Error : " + u);
                        return false;
                    }
                } catch (IOException | InterruptedException e) {
                    System.out.println(e.getMessage());
                    return false;
                }
            }).collect();

            if (generatedTargets.contains(false)) {
                System.out.println("\u001B[31mFatal Error: Exiting make due to task failure at level " + i + "\u001B[0m");
                break;
            }
        }
        sc.stop();
    }
}
