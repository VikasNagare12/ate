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
}

