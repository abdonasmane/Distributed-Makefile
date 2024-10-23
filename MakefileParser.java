import java.io.*;
import java.util.*;

public class MakefileParser {
    // Store targets and their dependencies
    private Map<String, List<String>> targets = new HashMap<>();
    // Store commands associated with targets
    private Map<String, List<String>> commands = new HashMap<>();
    // Graph for topological sorting (Adjacency list)
    private Map<String, List<String>> graph = new HashMap<>();
    // In-degree to track dependencies for topological sort
    private Map<String, Integer> inDegree = new HashMap<>();

    // Parse the Makefile
    public void parseMakefile(String filename) throws IOException {
        String line;
        String currentTarget = null;
        try (BufferedReader reader = new BufferedReader(new FileReader(filename))) {
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;

                if (line.contains(":")) {
                    // Target line
                    String[] parts = line.split(":");
                    String target = parts[0].trim();
                    List<String> dependencies = parts.length > 1 ? Arrays.asList(parts[1].trim().split("\\s+")) : Arrays.asList();

                    // Initialize the target and dependencies in the graph
                    targets.put(target, dependencies);
                    graph.putIfAbsent(target, new ArrayList<>());
                    inDegree.putIfAbsent(target, 0);  // Initialize in-degree for the target

                    // Ensure all dependencies are initialized in the graph
                    for (String dep : dependencies) {
                        graph.putIfAbsent(dep, new ArrayList<>());  // Initialize dependency in graph
                        inDegree.putIfAbsent(dep, 0);  // Ensure the dependency is tracked
                        graph.get(dep).add(target);    // Add dep -> target edge
                        inDegree.put(target, inDegree.getOrDefault(target, 0) + 1); // Increase in-degree for target
                    }

                    currentTarget = target;
                } else if (currentTarget != null) {
                    // Command line
                    commands.computeIfAbsent(currentTarget, k -> new ArrayList<>()).add(line);
                }
            }
        }
    }

    // Method to print the task graph (for debugging purposes)
    public void printGraph() {
        System.out.println("Task Graph (Dependencies):");
        for (String node : graph.keySet()) {
            System.out.println("Target: " + node + " -> " + graph.get(node));
        }
        System.out.println();
    }

    // Generate task graph and print the execution order
    public void generateTaskGraph() {
        List<List<String>> executionOrder = topologicalSort();
        if (executionOrder == null) {
            System.out.println("Error: Cyclic dependencies detected.");
        } else {
            System.out.println("Execution Order:");
            for (int i = 0; i < executionOrder.size(); i++) {
                System.out.println("Level " + i + " ##############################");
                for (String target : executionOrder.get(i)) {
                    System.out.println("\tTarget: " + target);
                    System.out.println("\tCommands: " + commands.get(target));
                    System.out.println();
                }
            }
        }
    }

    // Topological sorting using Kahn's Algorithm
    private List<List<String>> topologicalSort() {
        Queue<String> queue = new LinkedList<>();
        List<List<String>> sortedOrder = new ArrayList<>();

        // Initialize queue with nodes having no dependencies (in-degree 0)
        for (String target : inDegree.keySet()) {
            if (inDegree.get(target) == 0) {
                queue.add(target);
            }
        }
        int graphSize = graph.size();
        do {
            Queue<String> tempQueue = new LinkedList<>();
            List<String> tempSortedOrder = new ArrayList<>();
            graphSize -=  queue.size();
            while (!queue.isEmpty()) {
                String current = queue.poll();
                tempSortedOrder.add(current);
    
                // Process neighbors and reduce their in-degree
                for (String neighbor : graph.get(current)) {
                    inDegree.put(neighbor, inDegree.get(neighbor) - 1);
                    if (inDegree.get(neighbor) == 0) {
                        tempQueue.add(neighbor);
                    }
                }
            }
            queue = tempQueue;
            sortedOrder.add(tempSortedOrder);
        } while (graphSize > 0);
        

        // If we couldn't process all nodes, there was a cycle
        if (graphSize != 0) {
            return null; // Cycle detected
        }

        return sortedOrder;
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

        // Print the task graph for debugging
        parser.printGraph();

        // Generate and print the task graph
        parser.generateTaskGraph();
    }
}
