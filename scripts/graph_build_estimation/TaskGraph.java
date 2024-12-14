import java.io.Serializable;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class TaskGraph implements Serializable {
    private Map<String, List<String>> graph = new HashMap<>();
    private List<List<String>> topologicalOrder;
    private String rootTarget;
    private static final ConcurrentHashMap<String, Set<String>> assocTargetAllDependencies = new ConcurrentHashMap<>();

    public TaskGraph(Map<String, List<String>> targets, String rootTarget) {
        this.rootTarget = rootTarget;
        buildGraph(targets);
        populateAllDependencies(targets);
    }

    // Build the dependency graph, where dependencies point to dependents.
    private void buildGraph(Map<String, List<String>> targets) {
        Map<String, Integer> inDegree = new HashMap<>();
        Set<String> visited = new HashSet<>();
        Queue<String> queue = new LinkedList<>();
        queue.add(rootTarget);

        while (!queue.isEmpty()) {
            String currentTarget = queue.poll();
            if (visited.contains(currentTarget)) continue;
            visited.add(currentTarget);

            graph.putIfAbsent(currentTarget, new ArrayList<>());
            inDegree.putIfAbsent(currentTarget, 0);

            List<String> dependencies = targets.getOrDefault(currentTarget, new ArrayList<>());
            for (String dependency : dependencies) {
                graph.putIfAbsent(dependency, new ArrayList<>());
                inDegree.putIfAbsent(dependency, 0);

                graph.get(dependency).add(currentTarget);
                inDegree.put(currentTarget, inDegree.get(currentTarget) + 1);

                queue.add(dependency);
            }
        }
        topologicalSort(inDegree);
    }

    // Topological sorting using Kahn's Algorithm
    public void topologicalSort(Map<String, Integer> inDegree) {
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

    // Method to populate all dependencies
    private void populateAllDependencies(Map<String, List<String>> targets) {
        for (String targetsWithNoDeps : topologicalOrder.get(0)) {
            assocTargetAllDependencies.put(targetsWithNoDeps, new HashSet<>());
        }
        for (int i = 1; i < topologicalOrder.size(); i++) {
            List<String> currentLevel = topologicalOrder.get(i);
            currentLevel.parallelStream().forEach(target -> {
                List<String> directDeps = targets.getOrDefault(target, new ArrayList<>());
                Set<String> allDependencies = new HashSet<>();
                for (String directDep : directDeps) {
                    allDependencies.add(directDep);
                    allDependencies.addAll(assocTargetAllDependencies.get(directDep));
                }
                assocTargetAllDependencies.put(target, allDependencies);
            });
        }
    }
    
}
