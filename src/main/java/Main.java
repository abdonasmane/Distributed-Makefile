import java.io.IOException;
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
        TaskGraph taskGraph = new TaskGraph(parser.getTargets(), parser.getCommands());
        // taskGraph.printGraph();

        // Execute the Makefile
        MakeExecutor executor = new MakeExecutor(taskGraph, workingDirectory);
        executor.execute();
    }
}

