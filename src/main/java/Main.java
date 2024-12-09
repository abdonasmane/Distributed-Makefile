import java.io.IOException;

import org.apache.spark.SparkConf;
import org.apache.spark.api.java.JavaSparkContext;

import java.io.File;

public class Main {
    public static void main(String[] args) throws IOException {
        long startGlobalStartTime = System.nanoTime();
        if (args.length < 4) {
            System.out.println("Usage: java Main <path-to-makefile> <target> <spark://spark_master_ip:port> <nfs-mode=NFS/NO_NFS>");
            return;
        }

        String makefilePath = args[0];
        String target = args[1];
        String sparkUrl = args[2];
        String nfs = args[3];
        if (!nfs.equals("NFS") && !nfs.equals("NO_NFS")) {
            System.out.println("Invalid mode nfs-mode = " + nfs);
            return;
        }

        // Determine the working directory from the Makefile's path
        File makefile = new File(makefilePath);
        if (!makefile.exists() || !makefile.isFile()) {
            System.err.println("\u001B[31mError: Makefile not found at " + makefilePath + "\u001B[0m");
            System.exit(1);
        }

        String workingDirectory = makefile.getParent();

        // Parse the Makefile
        long startParsingTime = System.nanoTime();
        MakefileParser parser = new MakefileParser();
        parser.parseMakefile(makefilePath);
        if (!parser.targetExists(target)) {
            System.err.println("\u001B[31mError: Target '" + target + "' not found in the Makefile.\u001B[0m");
            System.exit(1);
        }
        long endParsingTime = System.nanoTime();

        // Build the task graph
        String isLocalhost = sc.master().contains(":") 
            ? sc.master().split(":")[1].substring(2)
            : "localhost";
        boolean localMod = isLocalhost.equals("localhost") || nfs.equals("NFS");
        long startBuildGraphTime = System.nanoTime();
        TaskGraph taskGraph = new TaskGraph(parser.getTargets(), target, localMod);
        if (taskGraph.getTopologicalOrder() == null) {
            System.err.println("\u001B[31mError: Cyclic dependencies detected. Cannot execute tasks.\u001B[0m");
            System.exit(1);
        }
        // taskGraph.printGraph();
        // taskGraph.printExecutionOrder();
        long endBuildGraphTime = System.nanoTime();

        // Initialize Spark context
        long startSparkConfTime = System.nanoTime();
        SparkConf conf = new SparkConf()
            .setAppName("DistributedMakeExecutor")
            .setMaster(sparkUrl);
        JavaSparkContext sc = new JavaSparkContext(conf);
        sc.setLogLevel("ERROR");
        long endSparkConfTime = System.nanoTime();

        // Execute the Makefile
        int serversPort = 8888;
        int fileLocatorPort = 9999;
        long startExecutionTime = System.nanoTime();
        if (isLocalhost.equals("localhost") || nfs.equals("NFS")) {
            LocalDistributedMakeExecutor executor = new LocalDistributedMakeExecutor(
                taskGraph, 
                parser.getCommands(), 
                workingDirectory, 
                sc
            );
            executor.execute();
        } else {
            DistributedMakeExecutor executor = new DistributedMakeExecutor(
                taskGraph, 
                parser.getCommands(), 
                workingDirectory, 
                serversPort, 
                fileLocatorPort, 
                sc
            );
            executor.execute();
        }
        long endExecutionTime = System.nanoTime();
        long endGlobalEndTime = System.nanoTime();
        long parsingTime = endParsingTime - startParsingTime;
        long buildGraphTime = endBuildGraphTime - startBuildGraphTime;
        long sparkConfTime = endSparkConfTime - startSparkConfTime;
        long executionTime = endExecutionTime - startExecutionTime;
        long globalExecutionTime = endGlobalEndTime - startGlobalStartTime;

        System.out.println("\n\u001B[32m==============================\u001B[0m");
        System.out.println("\u001B[32m  Global Time : " + (globalExecutionTime / 1e9) + " seconds  \u001B[0m");
        System.out.println("\u001B[32m==============================\u001B[0m");

        System.out.println("\u001B[33m--------------------------------\u001B[0m");
        System.out.println("\t\u001B[33mParsing Time           : " + (parsingTime / 1e9) + " seconds\u001B[0m");
        System.out.println("\t\u001B[33mGraph Build Time       : " + (buildGraphTime / 1e9) + " seconds\u001B[0m");
        System.out.println("\t\u001B[33mSpark Configuration Time: " + (sparkConfTime / 1e9) + " seconds\u001B[0m");
        System.out.println("\t\u001B[33mExecution Time         : " + (executionTime / 1e9) + " seconds\u001B[0m");
        System.out.println("\u001B[33m--------------------------------\u001B[0m");
    }
}

