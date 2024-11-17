import java.io.IOException;
import java.io.Serializable;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.spark.SparkContext;
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
    private JavaSparkContext sc;
    private ConcurrentHashMap<String, String> localFileMap;

    public DistributedMakeExecutor(TaskGraph taskGraph, Map<String, List<String>> commands, Map<String, List<String>> targets, String workingDirectory, int serversPort, JavaSparkContext sc) {
        this.taskGraph = taskGraph;
        this.commands = commands;
        this.targets = targets;
        this.workingDirectory = workingDirectory;
        this.serversPort = serversPort;
        this.sc = sc;
        this.localFileMap = new ConcurrentHashMap<>();
    }

    public void execute() {
        List<List<String>> executionOrder = taskGraph.getTopologicalOrder();
        Broadcast<Map<String, List<String>>> broadcastCommands = sc.broadcast(commands);
        Broadcast<Map<String, List<String>>> broadcastTargets = sc.broadcast(targets);
        Broadcast<String> broadcastWorkingDirectory = sc.broadcast(workingDirectory);
        Broadcast<Map<String, String>> broadcastFileMap = sc.broadcast(new HashMap<>());
        Broadcast<Integer> broadcastServerPort = sc.broadcast(serversPort);
    
        if (executionOrder == null) {
            System.out.println("\u001B[31mError: Cyclic dependencies detected. Cannot execute tasks.\u001B[0m");
            return;
        }

        // For each level in the task graph, execute in parallel using Spark
        for (int i = 0; i < executionOrder.size(); i++) {
            List<String> currentLevel = executionOrder.get(i);
            JavaRDD<String> tasksRDD = sc.parallelize(currentLevel);

            // Collect the results of each task execution
            List<String> generatedTargets = tasksRDD.map(target -> {
                try {
                    List<String> targetCommands = broadcastCommands.value().get(target);

                    if (targetCommands == null) {
                        System.out.println("\u001B[33mSkipping target '" + target + "' (no commands).\u001B[0m");
                        return "";
                    }

                    List<String> targetDependencies = broadcastTargets.value().get(target);
                    if (targetDependencies != null && !targetDependencies.isEmpty()) {
                        Map<String, String> globalFileMap = broadcastFileMap.value();
                        for (String dependency : targetDependencies) {
                            File file = new File(broadcastWorkingDirectory.value() + File.separator + dependency);
                            if (!file.exists() || !file.isFile()) {
                                String fileOwnerMachine = globalFileMap.get(dependency);
                                if (fileOwnerMachine == null) {
                                    System.out.println("\u001B[31mError: Cannot transfer file" + dependency + " for target '" + target + "' as its location is unknown.\u001B[0m");
                                    return "u4E06TtW6ypOAfYb3h5x";
                                }
                                System.out.println("\t\u001B[36mGetting File: " + dependency + "\u001B[0m");
                                GetFile.retrieveFile(fileOwnerMachine, broadcastServerPort.value(), dependency, broadcastWorkingDirectory.value()+File.separator+dependency);
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
                    return target;
                } catch (IOException | InterruptedException e) {
                    System.out.println(e.getMessage());
                    return "u4E06TtW6ypOAfYb3h5x";
                }
            }).filter(target -> target != null).collect();

            if (generatedTargets.contains("u4E06TtW6ypOAfYb3h5x")) {
                System.out.println("\u001B[31mFatal Error: Exiting make due to task failure at level " + i + "\u001B[0m");
                break;
            }
            try {
                String machineIp = InetAddress.getLocalHost().getHostAddress();
                for (String target : generatedTargets) {
                    localFileMap.put(target, machineIp);
                }
            } catch (UnknownHostException u) {
                System.out.println("Network Error : " + u);
                sc.stop();
                return;
            }

            // Combine the global and local file maps, then update the broadcast
            Map<String, String> globalFileMap = new HashMap<>(broadcastFileMap.value());
            globalFileMap.putAll(localFileMap);
            BroadcastWrapper.getInstance().updateAndGet(sc.sc(), globalFileMap);
            System.out.println("\u001B[35mUpdated file map broadcasted: " + globalFileMap + "\u001B[0m");
        }
        sc.stop();
    }
}
