import java.io.IOException;

import org.apache.spark.SparkConf;
import org.apache.spark.api.java.JavaSparkContext;

import java.io.File;

public class Main {
    public static void main(String[] args) throws IOException {
        long startTime = System.nanoTime();
        if (args.length < 3) {
            System.out.println("Usage: java Main <path-to-makefile> <target> <spark://spark_master_ip:port>");
            return;
        }

        String makefilePath = args[0];
        String target = args[1];
        String sparkUrl = args[2];

        // Determine the working directory from the Makefile's path
        File makefile = new File(makefilePath);
        if (!makefile.exists() || !makefile.isFile()) {
            System.err.println("\u001B[31mError: Makefile not found at " + makefilePath + "\u001B[0m");
            System.exit(1);
        }

        String workingDirectory = makefile.getParent();

        // Parse the Makefile
        MakefileParser parser = new MakefileParser();
        parser.parseMakefile(makefilePath);
        if (!parser.targetExists(target)) {
            System.err.println("\u001B[31mError: Target '" + target + "' not found in the Makefile.\u001B[0m");
            System.exit(1);
        }

        // Build the task graph
        TaskGraph taskGraph = new TaskGraph(parser.getTargets(), target);
        if (taskGraph.getTopologicalOrder() == null) {
            System.err.println("\u001B[31mError: Cyclic dependencies detected. Cannot execute tasks.\u001B[0m");
            System.exit(1);
        }
        taskGraph.printGraph();
        taskGraph.printExecutionOrder();

        // Initialize Spark context
        SparkConf conf = new SparkConf()
            .setAppName("DistributedMakeExecutor")
            .setMaster(sparkUrl);
        JavaSparkContext sc = new JavaSparkContext(conf);
        sc.setLogLevel("ERROR");

        // Execute the Makefile
        int serversPort = 8888;
        int fileLocatorPort = 9999;
        DistributedMakeExecutor executor = new DistributedMakeExecutor(
            taskGraph, 
            parser.getCommands(), 
            parser.getTargets(), 
            workingDirectory, 
            serversPort, 
            fileLocatorPort, 
            sc
        );
        executor.execute();
        long endTime = System.nanoTime();
        long executionTime = endTime - startTime;
        System.out.println("\n\u001B[33mExecution Time : " + (executionTime/1e9) + " seconds\u001B[0m");
    }
}

