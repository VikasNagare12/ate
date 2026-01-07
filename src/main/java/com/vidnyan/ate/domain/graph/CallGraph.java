package com.vidnyan.ate.domain.graph;

import com.vidnyan.ate.domain.model.SourceModel;

import java.util.*;

/**
 * Precomputed call graph for efficient traversal.
 * Bidirectional index: caller → callees, callee → callers.
 * Immutable and thread-safe.
 */
public final class CallGraph {
    
    private static final int MAX_DEPTH = 100;
    
    private final Map<String, List<CallEdge>> outgoing;
    private final Map<String, List<CallEdge>> incoming;
    private final Set<String> applicationMethods;
    
    private CallGraph(
            Map<String, List<CallEdge>> outgoing,
            Map<String, List<CallEdge>> incoming,
            Set<String> applicationMethods
    ) {
        this.outgoing = Collections.unmodifiableMap(outgoing);
        this.incoming = Collections.unmodifiableMap(incoming);
        this.applicationMethods = Collections.unmodifiableSet(applicationMethods);
    }
    
    /**
     * Build call graph from source model.
     */
    public static CallGraph build(SourceModel model, List<CallEdge> edges) {
        Map<String, List<CallEdge>> outgoing = new HashMap<>();
        Map<String, List<CallEdge>> incoming = new HashMap<>();
        Set<String> appMethods = new HashSet<>(model.methods().keySet());
        
        for (CallEdge edge : edges) {
            outgoing.computeIfAbsent(edge.callerFqn(), k -> new ArrayList<>()).add(edge);
            
            String target = edge.effectiveCalleeFqn();
            incoming.computeIfAbsent(target, k -> new ArrayList<>()).add(edge);
        }
        
        return new CallGraph(outgoing, incoming, appMethods);
    }
    
    /**
     * Get all outgoing call edges from a method.
     */
    public List<CallEdge> getOutgoingCalls(String methodFqn) {
        return outgoing.getOrDefault(methodFqn, List.of());
    }

    /**
     * Get all method FQNs that have outgoing calls.
     */
    public Set<String> getMethodsWithOutgoingCalls() {
        return outgoing.keySet();
    }

    /**
     * Get all methods defined in the application.
     */
    public Set<String> getApplicationMethods() {
        return applicationMethods;
    }

    /**
     * Get all incoming call edges to a method.
     */
    public List<CallEdge> getIncomingCalls(String methodFqn) {
        return incoming.getOrDefault(methodFqn, List.of());
    }
    
    /**
     * Get callee FQNs (resolved where available).
     */
    public List<String> getCallees(String methodFqn) {
        return getOutgoingCalls(methodFqn).stream()
                .map(CallEdge::effectiveCalleeFqn)
                .toList();
    }
    
    /**
     * Get caller FQNs.
     */
    public List<String> getCallers(String methodFqn) {
        return getIncomingCalls(methodFqn).stream()
                .map(CallEdge::callerFqn)
                .toList();
    }
    
    /**
     * Check if a method is from the application (not a library).
     */
    public boolean isApplicationMethod(String methodFqn) {
        return applicationMethods.contains(methodFqn);
    }
    
    /**
     * Check if a method is from an external library.
     */
    public boolean isLibraryMethod(String methodFqn) {
        return !isApplicationMethod(methodFqn);
    }
    
    /**
     * Find all methods reachable from a start method.
     * Stops at library boundaries.
     */
    public Set<String> findReachableMethods(String startMethod) {
        Set<String> visited = new HashSet<>();
        Queue<String> queue = new LinkedList<>();
        queue.add(startMethod);
        
        while (!queue.isEmpty()) {
            String method = queue.poll();
            if (visited.contains(method) || visited.size() > MAX_DEPTH * 100) {
                continue;
            }
            visited.add(method);
            
            if (isLibraryMethod(method)) {
                continue; // Don't traverse into libraries
            }
            
            for (String callee : getCallees(method)) {
                if (!visited.contains(callee)) {
                    queue.add(callee);
                }
            }
        }
        
        return visited;
    }
    
    /**
     * Find all call chains from start to target method.
     */
    public List<List<String>> findCallChains(String startMethod, String targetMethod) {
        List<List<String>> allChains = new ArrayList<>();
        findChainsRecursive(startMethod, targetMethod, new ArrayList<>(), new HashSet<>(), allChains, 0);
        return allChains;
    }
    
    private void findChainsRecursive(
            String current, String target,
            List<String> chain, Set<String> visited,
            List<List<String>> allChains, int depth
    ) {
        if (depth > MAX_DEPTH || visited.contains(current)) {
            return;
        }
        
        chain.add(current);
        visited.add(current);
        
        if (current.equals(target)) {
            allChains.add(new ArrayList<>(chain));
        } else if (!isLibraryMethod(current)) {
            for (String callee : getCallees(current)) {
                findChainsRecursive(callee, target, chain, visited, allChains, depth + 1);
            }
        }
        
        chain.remove(chain.size() - 1);
        visited.remove(current);
    }
    
    /**
     * Find all call chains ending at a sink that matches a pattern.
     */
    public List<List<String>> findChainsToSink(String startMethod, String sinkPattern) {
        List<List<String>> allChains = new ArrayList<>();
        findChainsToSinkRecursive(startMethod, sinkPattern, new ArrayList<>(), new HashSet<>(), allChains, 0);
        return allChains;
    }
    
    private void findChainsToSinkRecursive(
            String current, String sinkPattern,
            List<String> chain, Set<String> visited,
            List<List<String>> allChains, int depth
    ) {
        if (depth > MAX_DEPTH || visited.contains(current)) {
            return;
        }
        
        chain.add(current);
        visited.add(current);
        
        // Check if current matches sink pattern
        if (current.startsWith(sinkPattern)) {
            allChains.add(new ArrayList<>(chain));
        } else if (!isLibraryMethod(current)) {
            for (String callee : getCallees(current)) {
                findChainsToSinkRecursive(callee, sinkPattern, chain, visited, allChains, depth + 1);
            }
        }
        
        chain.remove(chain.size() - 1);
        visited.remove(current);
    }
    
    /**
     * Format a call chain as a readable string.
     */
    public static String formatChain(List<String> chain) {
        return String.join(" → ", chain);
    }
    
    /**
     * Get graph statistics.
     */
    public Stats stats() {
        return new Stats(
                applicationMethods.size(),
                outgoing.values().stream().mapToInt(List::size).sum(),
                outgoing.size()
        );
    }
    
    public record Stats(int methodCount, int edgeCount, int nodesWithOutgoingCalls) {}
}
