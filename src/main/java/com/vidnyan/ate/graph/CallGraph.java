package com.vidnyan.ate.graph;

import com.vidnyan.ate.model.Method;
import com.vidnyan.ate.model.Relationship;
import com.vidnyan.ate.model.RelationshipType;
import com.vidnyan.ate.model.SourceModel;
import lombok.Builder;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;

import java.util.*;

/**
 * Precomputed call graph for efficient traversal.
 * Bidirectional index: caller → callees, callee → callers.
 */
@Slf4j
@Value
@Builder
public class CallGraph {
    // Outgoing calls: method → methods it calls
    Map<String, List<String>> outgoingCalls;
    
    // Incoming calls: method → methods that call it
    Map<String, List<String>> incomingCalls;
    
    // Call sites: method → list of call relationships
    Map<String, List<Relationship>> callSites;
    
    // Reference to source model for library boundary detection
    SourceModel sourceModel;
    
    /**
     * Build call graph from Source Model.
     */
    public static CallGraph build(SourceModel model) {
        Map<String, List<String>> outgoing = new HashMap<>();
        Map<String, List<String>> incoming = new HashMap<>();
        Map<String, List<Relationship>> sites = new HashMap<>();
        
        // Extract CALLS relationships
        List<Relationship> calls = model.getRelationships(RelationshipType.CALLS);
        
        for (Relationship call : calls) {
            String caller = call.getSourceEntityId();
            String callee = call.getTargetEntityId();
            
            // Outgoing
            outgoing.computeIfAbsent(caller, k -> new ArrayList<>()).add(callee);
            
            // Incoming
            incoming.computeIfAbsent(callee, k -> new ArrayList<>()).add(caller);
            
            // Call sites
            sites.computeIfAbsent(caller, k -> new ArrayList<>()).add(call);
        }
        
        return CallGraph.builder()
                .sourceModel(model)
                .outgoingCalls(Collections.unmodifiableMap(outgoing))
                .incomingCalls(Collections.unmodifiableMap(incoming))
                .callSites(Collections.unmodifiableMap(sites))
                .build();
    }
    
    /**
     * Get all methods called by a method.
     */
    public List<String> getCallees(String methodSignature) {
        return outgoingCalls.getOrDefault(methodSignature, List.of());
    }
    
    /**
     * Get all methods that call a method.
     */
    public List<String> getCallers(String methodSignature) {
        return incomingCalls.getOrDefault(methodSignature, List.of());
    }
    
    /**
     * Get all call sites for a method.
     */
    public List<Relationship> getCallSites(String methodSignature) {
        return callSites.getOrDefault(methodSignature, List.of());
    }
    
    /**
     * Check if a method is from an external library (not in our source model).
     * Library methods are treated as leaf nodes - we don't trace into them.
     */
    public boolean isLibraryMethod(String methodSignature) {
        // If method is not in our source model, it's a library method
        return sourceModel.getMethod(methodSignature) == null;
    }
    
    /**
     * Get callees, excluding library methods if requested.
     */
    public List<String> getCallees(String methodSignature, boolean excludeLibraries) {
        if (!excludeLibraries) {
            return getCallees(methodSignature);
        }
        
        return getCallees(methodSignature).stream()
                .filter(callee -> !isLibraryMethod(callee))
                .toList();
    }
    
    private static final int MAX_CALL_DEPTH = 100;

    /**
     * Find all methods reachable from a start method (transitive closure).
     * Stops at library boundaries by default.
     */
    public Set<String> findReachableMethods(String startMethod) {
        return findReachableMethods(startMethod, true);
    }
    
    /**
     * Find all methods reachable from a start method (transitive closure).
     * @param stopAtLibraries if true, don't traverse into library methods
     */
    public Set<String> findReachableMethods(String startMethod, boolean stopAtLibraries) {
        Set<String> visited = new HashSet<>();
        Queue<DepthNode> queue = new LinkedList<>();
        queue.add(new DepthNode(startMethod, 0));
        
        while (!queue.isEmpty()) {
            DepthNode node = queue.poll();
            if (node.depth > MAX_CALL_DEPTH) {
                log.error("Max call depth {} exceeded while traversing from {}", MAX_CALL_DEPTH, startMethod);
                continue;
            }
            if (visited.contains(node.method)) {
                continue;
            }
            visited.add(node.method);
            
            // Stop at library boundaries if requested
            if (stopAtLibraries && isLibraryMethod(node.method)) {
                continue;
            }
            
            for (String callee : getCallees(node.method)) {
                if (!visited.contains(callee)) {
                    queue.add(new DepthNode(callee, node.depth + 1));
                }
            }
        }
        
        return visited;
    }
    
    private record DepthNode(String method, int depth) {}
    
    /**
     * Find all call chains (execution paths) from a start method.
     * Returns a list of call chains, where each chain is a list of method signatures.
     * Stops at library boundaries - e.g., a→b→c→JdbcTemplate.query() stops at JdbcTemplate.
     * Example: [["a", "b", "c"], ["a", "b", "d"], ["a", "e"]]
     */
    public List<List<String>> findCallChains(String startMethod) {
        return findCallChains(startMethod, true);
    }
    
    /**
     * Find all call chains (execution paths) from a start method.
     * @param stopAtLibraries if true, treat library methods as leaf nodes
     */
    public List<List<String>> findCallChains(String startMethod, boolean stopAtLibraries) {
        List<List<String>> allChains = new ArrayList<>();
        List<String> currentChain = new ArrayList<>();
        Set<String> visited = new HashSet<>();
        
        findCallChainsRecursive(startMethod, currentChain, visited, allChains, 0, stopAtLibraries);
        
        return allChains;
    }
    
    private void findCallChainsRecursive(String method, List<String> currentChain, 
                                         Set<String> visited, List<List<String>> allChains,
            int currentDepth, boolean stopAtLibraries) {
        if (currentDepth > MAX_CALL_DEPTH) {
            log.error("Max call depth {} exceeded while tracing from {}", MAX_CALL_DEPTH,
                    currentChain.isEmpty() ? method : currentChain.get(0));
            return;
        }
        
        // Avoid cycles
        if (visited.contains(method)) {
            return;
        }
        
        // Add current method to chain
        currentChain.add(method);
        visited.add(method);
        
        // Check if this is a library method (external boundary)
        boolean isLibrary = stopAtLibraries && isLibraryMethod(method);
        List<String> callees = isLibrary ? List.of() : getCallees(method);
        
        if (callees.isEmpty() || currentDepth == MAX_CALL_DEPTH || isLibrary) {
            // Leaf node, max depth reached, or library boundary - save this chain
            allChains.add(new ArrayList<>(currentChain));
        } else {
            // Continue exploring
            for (String callee : callees) {
                findCallChainsRecursive(callee, currentChain, visited, allChains, currentDepth + 1, stopAtLibraries);
            }
        }
        
        // Backtrack
        currentChain.remove(currentChain.size() - 1);
        visited.remove(method);
    }
    
    /**
     * Find all call chains from start method to a target method.
     * Useful for tracing how a specific method is reached.
     * Stops at library boundaries by default.
     */
    public List<List<String>> findCallChainsToTarget(String startMethod, String targetMethod) {
        return findCallChainsToTarget(startMethod, targetMethod, true);
    }
    
    /**
     * Find all call chains from start method to a target method.
     * @param stopAtLibraries if true, don't traverse into library methods
     */
    public List<List<String>> findCallChainsToTarget(String startMethod, String targetMethod, boolean stopAtLibraries) {
        List<List<String>> allChains = new ArrayList<>();
        List<String> currentChain = new ArrayList<>();
        Set<String> visited = new HashSet<>();
        
        findCallChainsToTargetRecursive(startMethod, targetMethod, currentChain, visited, allChains, 0,
                stopAtLibraries);
        
        return allChains;
    }
    
    private void findCallChainsToTargetRecursive(String method, String targetMethod,
                                                  List<String> currentChain, Set<String> visited,
            List<List<String>> allChains, int currentDepth,
                                                          boolean stopAtLibraries) {
        if (currentDepth > MAX_CALL_DEPTH) {
            log.error("Max call depth {} exceeded while tracing from {} to {}", MAX_CALL_DEPTH, currentChain.get(0),
                    targetMethod);
            return;
        }
        
        // Avoid cycles
        if (visited.contains(method)) {
            return;
        }
        
        // Add current method to chain
        currentChain.add(method);
        visited.add(method);
        
        // Check if we reached the target
        if (method.equals(targetMethod)) {
            allChains.add(new ArrayList<>(currentChain));
        } else if (!stopAtLibraries || !isLibraryMethod(method)) {
            // Continue exploring (unless we hit a library boundary)
            List<String> callees = getCallees(method);
            for (String callee : callees) {
                findCallChainsToTargetRecursive(callee, targetMethod, currentChain, visited, allChains,
                        currentDepth + 1, stopAtLibraries);
            }
        }
        
        // Backtrack
        currentChain.remove(currentChain.size() - 1);
        visited.remove(method);
    }
    
    /**
     * Find transaction boundaries - traces all call chains from @Transactional methods.
     * This helps identify transaction propagation paths.
     */
    public Map<String, List<List<String>>> findTransactionBoundaries(Set<String> transactionalMethods) {
        Map<String, List<List<String>>> transactionChains = new HashMap<>();
        
        for (String txMethod : transactionalMethods) {
            List<List<String>> chains = findCallChains(txMethod);
            if (!chains.isEmpty()) {
                transactionChains.put(txMethod, chains);
            }
        }
        
        return transactionChains;
    }
    
    /**
     * Format a call chain as a readable string.
     * Example: "AnalysisEngine.analyze() → RepositoryScanner.scan() → FileUtils.readFile()"
     */
    public static String formatCallChain(List<String> chain) {
        return String.join(" → ", chain);
    }
}

