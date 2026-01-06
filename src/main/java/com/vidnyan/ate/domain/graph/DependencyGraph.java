package com.vidnyan.ate.domain.graph;

import com.vidnyan.ate.domain.model.SourceModel;
import com.vidnyan.ate.domain.model.TypeEntity;

import java.util.*;

/**
 * Package-level dependency graph for architectural analysis.
 * Used for circular dependency detection and layered architecture validation.
 */
public final class DependencyGraph {
    
    private final Map<String, Set<String>> dependencies; // package → packages it depends on
    private final Map<String, Set<String>> dependents;   // package → packages that depend on it
    private final Set<String> packages;
    
    private DependencyGraph(
            Map<String, Set<String>> dependencies,
            Map<String, Set<String>> dependents,
            Set<String> packages
    ) {
        this.dependencies = Collections.unmodifiableMap(dependencies);
        this.dependents = Collections.unmodifiableMap(dependents);
        this.packages = Collections.unmodifiableSet(packages);
    }
    
    /**
     * Build dependency graph from source model.
     */
    public static DependencyGraph build(SourceModel model) {
        Map<String, Set<String>> deps = new HashMap<>();
        Map<String, Set<String>> revDeps = new HashMap<>();
        Set<String> pkgs = new HashSet<>();
        
        for (TypeEntity type : model.types().values()) {
            String pkg = type.packageName();
            pkgs.add(pkg);
            
            // Add dependencies from supertypes
            for (var supertype : type.supertypes()) {
                String depPkg = extractPackage(supertype.fullyQualifiedName());
                if (!depPkg.isEmpty() && !depPkg.equals(pkg)) {
                    deps.computeIfAbsent(pkg, k -> new HashSet<>()).add(depPkg);
                    revDeps.computeIfAbsent(depPkg, k -> new HashSet<>()).add(pkg);
                }
            }
            
            // Add dependencies from interfaces
            for (var iface : type.interfaces()) {
                String depPkg = extractPackage(iface.fullyQualifiedName());
                if (!depPkg.isEmpty() && !depPkg.equals(pkg)) {
                    deps.computeIfAbsent(pkg, k -> new HashSet<>()).add(depPkg);
                    revDeps.computeIfAbsent(depPkg, k -> new HashSet<>()).add(pkg);
                }
            }
        }
        
        // Add dependencies from fields
        for (var field : model.fields().values()) {
            String typePkg = extractPackage(field.containingTypeFqn());
            String fieldTypePkg = extractPackage(field.type().fullyQualifiedName());
            if (!fieldTypePkg.isEmpty() && !fieldTypePkg.equals(typePkg)) {
                deps.computeIfAbsent(typePkg, k -> new HashSet<>()).add(fieldTypePkg);
                revDeps.computeIfAbsent(fieldTypePkg, k -> new HashSet<>()).add(typePkg);
            }
        }
        
        return new DependencyGraph(deps, revDeps, pkgs);
    }
    
    /**
     * Get packages that a package depends on.
     */
    public Set<String> getDependencies(String packageName) {
        return dependencies.getOrDefault(packageName, Set.of());
    }
    
    /**
     * Get packages that depend on a package.
     */
    public Set<String> getDependents(String packageName) {
        return dependents.getOrDefault(packageName, Set.of());
    }
    
    /**
     * Check if there are any circular dependencies.
     */
    public boolean hasCircularDependencies() {
        return !findCircularDependencies().isEmpty();
    }
    
    /**
     * Find all circular dependencies.
     * Returns list of cycles, where each cycle is a list of package names.
     */
    public List<List<String>> findCircularDependencies() {
        List<List<String>> cycles = new ArrayList<>();
        Set<String> visited = new HashSet<>();
        Set<String> inStack = new HashSet<>();
        
        for (String pkg : packages) {
            if (!visited.contains(pkg)) {
                findCyclesRecursive(pkg, visited, inStack, new ArrayList<>(), cycles);
            }
        }
        
        return cycles;
    }
    
    private void findCyclesRecursive(
            String current,
            Set<String> visited,
            Set<String> inStack,
            List<String> path,
            List<List<String>> cycles
    ) {
        visited.add(current);
        inStack.add(current);
        path.add(current);
        
        for (String dep : getDependencies(current)) {
            if (!visited.contains(dep)) {
                findCyclesRecursive(dep, visited, inStack, path, cycles);
            } else if (inStack.contains(dep)) {
                // Found cycle - extract it
                int cycleStart = path.indexOf(dep);
                List<String> cycle = new ArrayList<>(path.subList(cycleStart, path.size()));
                cycle.add(dep); // Complete the cycle
                cycles.add(cycle);
            }
        }
        
        path.remove(path.size() - 1);
        inStack.remove(current);
    }
    
    /**
     * Check if packageA depends on packageB (directly or transitively).
     */
    public boolean dependsOn(String packageA, String packageB) {
        Set<String> visited = new HashSet<>();
        Queue<String> queue = new LinkedList<>();
        queue.add(packageA);
        
        while (!queue.isEmpty()) {
            String pkg = queue.poll();
            if (visited.contains(pkg)) continue;
            visited.add(pkg);
            
            Set<String> deps = getDependencies(pkg);
            if (deps.contains(packageB)) {
                return true;
            }
            queue.addAll(deps);
        }
        
        return false;
    }
    
    /**
     * Extract package name from FQN.
     */
    private static String extractPackage(String fqn) {
        int lastDot = fqn.lastIndexOf('.');
        return lastDot > 0 ? fqn.substring(0, lastDot) : "";
    }
    
    /**
     * Get all packages.
     */
    public Set<String> getAllPackages() {
        return packages;
    }
    
    /**
     * Get statistics.
     */
    public Stats stats() {
        return new Stats(
                packages.size(),
                dependencies.values().stream().mapToInt(Set::size).sum()
        );
    }
    
    public record Stats(int packageCount, int dependencyCount) {}
}
