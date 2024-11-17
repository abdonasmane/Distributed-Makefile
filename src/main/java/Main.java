import java.io.IOException;

import org.apache.spark.SparkConf;
import org.apache.spark.api.java.JavaSparkContext;

import java.io.File;

public class Main {
    public static void main(String[] args) throws IOException {
        if (args.length < 1) {
            System.out.println("Usage: java Main <path-to-makefile>");
            return;
        }

        String makefilePath = args[0];

        // Determine the working directory from the Makefile's path
        File makefile = new File(makefilePath);
        String workingDirectory = makefile.getParent();

        // Parse the Makefile
        MakefileParser parser = new MakefileParser();
        parser.parseMakefile(makefilePath);

        // Build the task graph
        TaskGraph taskGraph = new TaskGraph(parser.getTargets());
        // taskGraph.printGraph();

        // Initialize Spark context
        SparkConf conf = new SparkConf().setAppName("MakeExecutor").setMaster("local[*]");
        JavaSparkContext sc = new JavaSparkContext(conf);
        sc.setLogLevel("ERROR");

        // Execute the Makefile
        int serversPort = 3000;
        DistributedMakeExecutor executor = new DistributedMakeExecutor(taskGraph, parser.getCommands(), parser.getTargets(), workingDirectory, serversPort, sc);
        executor.execute();
    }
}

