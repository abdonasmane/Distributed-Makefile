import java.io.*;
import java.util.*;

public class MakefileParser {
    // Store targets and their dependencies
    private Map<String, List<String>> targets = new HashMap<>();
    // Store commands associated with targets
    private Map<String, List<String>> commands = new HashMap<>();

    public void parseMakefile(String filename) throws IOException {
        String line;
        String currentTarget = null;
        try (BufferedReader reader = new BufferedReader(new FileReader(filename))) {
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                // Skip empty lines or comments
                if (line.isEmpty() || line.startsWith("#")) continue;

                if (line.contains(":")) {
                    // Target line
                    String[] parts = line.split(":");
                    String target = parts[0].trim();
                    List<String> dependencies = Arrays.asList(parts[1].trim().split("\\s+"));
                    targets.put(target, dependencies);
                    currentTarget = target;
                } else if (currentTarget != null) {
                    // Command line
                    commands.computeIfAbsent(currentTarget, k -> new ArrayList<>()).add(line);
                }
            }
        }
    }

    public void printMakefile() {
        for (String target : targets.keySet()) {
            System.out.println("Target: " + target);
            System.out.println("Dependencies: " + targets.get(target));
            System.out.println("Commands: " + commands.get(target));
            System.out.println();
        }
    }

    public static void main(String[] args) throws IOException {
        // Check if a file path is provided as an argument
        if (args.length < 1) {
            System.out.println("Usage: java MakefileParser <path-to-makefile>");
            return; // Exit if no argument is provided
        }
        // Get the file path from the argument
        String makefilePath = args[0];

        // Create a MakefileParser object
        MakefileParser parser = new MakefileParser();

        // Parse the provided Makefile
        parser.parseMakefile(makefilePath);

        // Print the parsed Makefile
        parser.printMakefile();
    }
}

