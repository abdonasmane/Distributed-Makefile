import java.io.Serializable;
import java.util.*;

public class TaskGraph implements Serializable {
    private Map<String, List<String>> graph = new HashMap<>();
    private Map<String, Integer> inDegree = new HashMap<>();
    private List<List<String>> topologicalOrder;

    public TaskGraph(Map<String, List<String>> targets) {
        buildGraph(targets);
        topologicalSort();
    }

    public List<List<String>> getTopologicalOrder() {
        return topologicalOrder;
    }

    // Build the dependency graph
    private void buildGraph(Map<String, List<String>> targets) {
        for (String target : targets.keySet()) {
            List<String> dependencies = targets.get(target);
            graph.putIfAbsent(target, new ArrayList<>());
            inDegree.putIfAbsent(target, 0);

            for (String dep : dependencies) {
                graph.putIfAbsent(dep, new ArrayList<>());
                inDegree.putIfAbsent(dep, 0);
                graph.get(dep).add(target);
                inDegree.put(target, inDegree.getOrDefault(target, 0) + 1);
            }
        }
    }

    // Print the graph for debugging
    public void printGraph() {
        System.out.println("Task Graph (Dependencies):");
        for (String node : graph.keySet()) {
            System.out.println("Target: " + node + " -> " + graph.get(node));
        }
        System.out.println();
    }

    // Topological sorting using Kahn's Algorithm
    public void topologicalSort() {
        Queue<String> queue = new LinkedList<>();
        List<List<String>> sortedOrder = new ArrayList<>();

        for (String target : inDegree.keySet()) {
            if (inDegree.get(target) == 0) {
                queue.add(target);
            }
        }

        int graphSize = graph.size();
        do {
            Queue<String> tempQueue = new LinkedList<>();
            List<String> tempSortedOrder = new ArrayList<>();
            graphSize -= queue.size();

            while (!queue.isEmpty()) {
                String current = queue.poll();
                tempSortedOrder.add(current);

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

        if (graphSize != 0) {
            topologicalOrder = null; // Cycle detected
        } else {
            topologicalOrder = sortedOrder;
        }
    }

    // // Print the execution order
    // public void printExecutionOrder() {
    //     List<List<String>> executionOrder = topologicalSort();
    //     if (executionOrder == null) {
    //         System.out.println("Error: Cyclic dependencies detected.");
    //     } else {
    //         System.out.println("Execution Order:");
    //         for (int i = 0; i < executionOrder.size(); i++) {
    //             System.out.println("Level " + i + " ##############################");
    //             for (String target : executionOrder.get(i)) {
    //                 System.out.println("\tTarget: " + target);
    //                 System.out.println("\tCommands: " + commands.get(target));
    //                 System.out.println();
    //             }
    //         }
    //     }
    // }   
}
