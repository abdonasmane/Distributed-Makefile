import java.io.*;
import java.util.*;

public class MakefileParser {
    private Map<String, List<String>> targets = new HashMap<>();
    private Map<String, List<String>> commands = new HashMap<>();

    public Map<String, List<String>> getTargets() {
        return targets;
    }

    public Map<String, List<String>> getCommands() {
        return commands;
    }

    // Parse the Makefile
    public void parseMakefile(String filename) throws IOException {
        String line;
        String currentTarget = null;
        try (BufferedReader reader = new BufferedReader(new FileReader(filename))) {
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;

                if (line.contains(":")) {
                    String[] parts = line.split(":");
                    String target = parts[0].trim();
                    List<String> dependencies = parts.length > 1 ? Arrays.asList(parts[1].trim().split("\\s+")) : Arrays.asList();

                    targets.put(target, dependencies);
                    currentTarget = target;
                } else if (currentTarget != null) {
                    commands.computeIfAbsent(currentTarget, k -> new ArrayList<>()).add(line);
                }
            }
        }
    }
}
