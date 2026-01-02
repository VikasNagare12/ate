package com.vidnyan.ate.graph;

import com.vidnyan.ate.model.Method;
import com.vidnyan.ate.model.Relationship;
import com.vidnyan.ate.model.RelationshipType;
import com.vidnyan.ate.model.SourceModel;
import lombok.Builder;
import lombok.Value;

import java.util.*;

/**
 * Precomputed call graph for efficient traversal.
 * Bidirectional index: caller → callees, callee → callers.
 */
@Value
@Builder
public class CallGraph {
    // Outgoing calls: method → methods it calls
    Map<String, List<String>> outgoingCalls;
    
    // Incoming calls: method → methods that call it
    Map<String, List<String>> incomingCalls;
    
    // Call sites: method → list of call relationships
    Map<String, List<Relationship>> callSites;
    
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
     * Find all methods reachable from a start method (transitive closure).
     */
    public Set<String> findReachableMethods(String startMethod, int maxDepth) {
        Set<String> visited = new HashSet<>();
        Queue<DepthNode> queue = new LinkedList<>();
        queue.add(new DepthNode(startMethod, 0));
        
        while (!queue.isEmpty()) {
            DepthNode node = queue.poll();
            if (node.depth > maxDepth || visited.contains(node.method)) {
                continue;
            }
            visited.add(node.method);
            
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
     * Example: [["a", "b", "c"], ["a", "b", "d"], ["a", "e"]]
     */
    public List<List<String>> findCallChains(String startMethod, int maxDepth) {
        List<List<String>> allChains = new ArrayList<>();
        List<String> currentChain = new ArrayList<>();
        Set<String> visited = new HashSet<>();
        
        findCallChainsRecursive(startMethod, currentChain, visited, allChains, maxDepth, 0);
        
        return allChains;
    }
    
    private void findCallChainsRecursive(String method, List<String> currentChain, 
                                         Set<String> visited, List<List<String>> allChains,
                                         int maxDepth, int currentDepth) {
        if (currentDepth > maxDepth) {
            return;
        }
        
        // Avoid cycles
        if (visited.contains(method)) {
            return;
        }
        
        // Add current method to chain
        currentChain.add(method);
        visited.add(method);
        
        List<String> callees = getCallees(method);
        
        if (callees.isEmpty() || currentDepth == maxDepth) {
            // Leaf node or max depth reached - save this chain
            allChains.add(new ArrayList<>(currentChain));
        } else {
            // Continue exploring
            for (String callee : callees) {
                findCallChainsRecursive(callee, currentChain, visited, allChains, maxDepth, currentDepth + 1);
            }
        }
        
        // Backtrack
        currentChain.remove(currentChain.size() - 1);
        visited.remove(method);
    }
    
    /**
     * Find all call chains from start method to a target method.
     * Useful for tracing how a specific method is reached.
     */
    public List<List<String>> findCallChainsToTarget(String startMethod, String targetMethod, int maxDepth) {
        List<List<String>> allChains = new ArrayList<>();
        List<String> currentChain = new ArrayList<>();
        Set<String> visited = new HashSet<>();
        
        findCallChainsToTargetRecursive(startMethod, targetMethod, currentChain, visited, allChains, maxDepth, 0);
        
        return allChains;
    }
    
    private void findCallChainsToTargetRecursive(String method, String targetMethod,
                                                  List<String> currentChain, Set<String> visited,
                                                  List<List<String>> allChains, int maxDepth, int currentDepth) {
        if (currentDepth > maxDepth) {
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
        } else {
            // Continue exploring
            List<String> callees = getCallees(method);
            for (String callee : callees) {
                findCallChainsToTargetRecursive(callee, targetMethod, currentChain, visited, allChains, maxDepth, currentDepth + 1);
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
    public Map<String, List<List<String>>> findTransactionBoundaries(Set<String> transactionalMethods, int maxDepth) {
        Map<String, List<List<String>>> transactionChains = new HashMap<>();
        
        for (String txMethod : transactionalMethods) {
            List<List<String>> chains = findCallChains(txMethod, maxDepth);
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

