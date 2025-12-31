package com.vidnyan.ate.graph;

import com.vidnyan.ate.model.SourceModel;
import com.vidnyan.ate.model.Type;
import lombok.Builder;
import lombok.Value;

import java.util.*;

/**
 * Precomputed dependency graph at package/module level.
 * Detects circular dependencies.
 */
@Value
@Builder
public class DependencyGraph {
    // Package → packages it depends on
    Map<String, Set<String>> packageDependencies;
    
    // Package → packages that depend on it
    Map<String, Set<String>> reverseDependencies;
    
    // Detected circular dependencies
    List<List<String>> circularDependencies;
    
    /**
     * Build dependency graph from Source Model.
     */
    public static DependencyGraph build(SourceModel model) {
        Map<String, Set<String>> deps = new HashMap<>();
        Map<String, Set<String>> reverse = new HashMap<>();
        
        // Build package dependencies from type references
        for (Type type : model.getTypes().values()) {
            String sourcePackage = type.getPackageName();
            
            // Check super types
            for (var superType : type.getSuperTypes()) {
                String targetPackage = extractPackage(superType.getFullyQualifiedName());
                if (!sourcePackage.equals(targetPackage) && !targetPackage.isEmpty()) {
                    deps.computeIfAbsent(sourcePackage, k -> new HashSet<>()).add(targetPackage);
                    reverse.computeIfAbsent(targetPackage, k -> new HashSet<>()).add(sourcePackage);
                }
            }
            
            // Check field types
            for (var field : type.getFields()) {
                String targetPackage = extractPackage(field.getType().getFullyQualifiedName());
                if (!sourcePackage.equals(targetPackage) && !targetPackage.isEmpty()) {
                    deps.computeIfAbsent(sourcePackage, k -> new HashSet<>()).add(targetPackage);
                    reverse.computeIfAbsent(targetPackage, k -> new HashSet<>()).add(sourcePackage);
                }
            }
            
            // Check method return types and parameters
            for (var method : type.getMethods()) {
                String returnPackage = extractPackage(method.getReturnType().getFullyQualifiedName());
                if (!sourcePackage.equals(returnPackage) && !returnPackage.isEmpty()) {
                    deps.computeIfAbsent(sourcePackage, k -> new HashSet<>()).add(returnPackage);
                    reverse.computeIfAbsent(returnPackage, k -> new HashSet<>()).add(sourcePackage);
                }
                
                for (var param : method.getParameters()) {
                    String paramPackage = extractPackage(param.getType().getFullyQualifiedName());
                    if (!sourcePackage.equals(paramPackage) && !paramPackage.isEmpty()) {
                        deps.computeIfAbsent(sourcePackage, k -> new HashSet<>()).add(paramPackage);
                        reverse.computeIfAbsent(paramPackage, k -> new HashSet<>()).add(sourcePackage);
                    }
                }
            }
        }
        
        // Detect circular dependencies using DFS
        List<List<String>> cycles = detectCycles(deps);
        
        return DependencyGraph.builder()
                .packageDependencies(Collections.unmodifiableMap(deps))
                .reverseDependencies(Collections.unmodifiableMap(reverse))
                .circularDependencies(Collections.unmodifiableList(cycles))
                .build();
    }
    
    /**
     * Extract package name from FQN.
     */
    private static String extractPackage(String fqn) {
        int lastDot = fqn.lastIndexOf('.');
        return lastDot > 0 ? fqn.substring(0, lastDot) : "";
    }
    
    /**
     * Detect circular dependencies using DFS.
     */
    private static List<List<String>> detectCycles(Map<String, Set<String>> graph) {
        List<List<String>> cycles = new ArrayList<>();
        Set<String> visited = new HashSet<>();
        Set<String> recStack = new HashSet<>();
        Map<String, String> parent = new HashMap<>();
        
        for (String node : graph.keySet()) {
            if (!visited.contains(node)) {
                dfs(node, graph, visited, recStack, parent, cycles);
            }
        }
        
        return cycles;
    }
    
    private static void dfs(String node, Map<String, Set<String>> graph,
                           Set<String> visited, Set<String> recStack,
                           Map<String, String> parent, List<List<String>> cycles) {
        visited.add(node);
        recStack.add(node);
        
        for (String neighbor : graph.getOrDefault(node, Collections.emptySet())) {
            if (!visited.contains(neighbor)) {
                parent.put(neighbor, node);
                dfs(neighbor, graph, visited, recStack, parent, cycles);
            } else if (recStack.contains(neighbor)) {
                // Found a cycle
                List<String> cycle = new ArrayList<>();
                String current = node;
                while (current != null && !current.equals(neighbor)) {
                    cycle.add(current);
                    current = parent.get(current);
                }
                cycle.add(neighbor);
                cycle.add(node); // Complete the cycle
                Collections.reverse(cycle);
                cycles.add(cycle);
            }
        }
        
        recStack.remove(node);
    }
    
    /**
     * Get packages that a package depends on.
     */
    public Set<String> getDependencies(String packageName) {
        return packageDependencies.getOrDefault(packageName, Collections.emptySet());
    }
    
    /**
     * Get packages that depend on a package.
     */
    public Set<String> getDependents(String packageName) {
        return reverseDependencies.getOrDefault(packageName, Collections.emptySet());
    }
    
    /**
     * Check if there are circular dependencies.
     */
    public boolean hasCircularDependencies() {
        return !circularDependencies.isEmpty();
    }
}

