import java.io.IOException;
import java.io.File;

public class Main {
    public static void main(String[] args) throws IOException {
        if (args.length < 2) {
            System.out.println("Usage: java Main <path-to-makefile> <target>");
            return;
        }

        String makefilePath = args[0];
        String target = args[1];

        // Determine the working directory from the Makefile's path
        File makefile = new File(makefilePath);
        if (!makefile.exists() || !makefile.isFile()) {
            System.err.println("\u001B[31mError: Makefile not found at " + makefilePath + "\u001B[0m");
            System.exit(1);
        }

        // Parse the Makefile
        MakefileParser parser = new MakefileParser();
        parser.parseMakefile(makefilePath);
        if (!parser.targetExists(target)) {
            System.err.println("\u001B[31mError: Target '" + target + "' not found in the Makefile.\u001B[0m");
            System.exit(1);
        }

        // Build the task graph
        long startBuildGraphTime = System.nanoTime();
        TaskGraph taskGraph = new TaskGraph(parser.getTargets(), target);
        // taskGraph.printGraph();
        // taskGraph.printExecutionOrder();
        // taskGraph.printTree();
        long endBuildGraphTime = System.nanoTime();
        long buildGraphTime = endBuildGraphTime - startBuildGraphTime;

        System.out.println("\u001B[33m--------------------------------\u001B[0m");
        System.out.println("\t\u001B[33mGraph Build Time       : " + (buildGraphTime / 1e9) + " seconds\u001B[0m");
        System.out.println("\u001B[33m--------------------------------\u001B[0m");
    }
}

