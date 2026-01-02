package com.vidnyan.ate.examples;

import com.vidnyan.ate.graph.CallGraph;
import com.vidnyan.ate.model.Method;
import com.vidnyan.ate.model.SourceModel;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Example demonstrating how to use CallGraph for transaction tracking.
 * This shows how to trace method call chains like a→b→c→d.
 */
public class CallChainExample {
    
    /**
     * Example 1: Find all call chains from a specific method.
     * Use case: Trace all execution paths from a controller endpoint.
     */
    public static void traceEndpointExecution(CallGraph callGraph, String controllerMethod) {
        System.out.println("=== Tracing execution paths from: " + controllerMethod + " ===");
        
        // Find all call chains up to depth 5
        List<List<String>> chains = callGraph.findCallChains(controllerMethod, 5);
        
        System.out.println("Found " + chains.size() + " execution paths:");
        for (int i = 0; i < chains.size(); i++) {
            System.out.println((i + 1) + ". " + CallGraph.formatCallChain(chains.get(i)));
        }
    }
    
    /**
     * Example 2: Find how a specific method is reached.
     * Use case: Trace all paths that lead to a database operation.
     */
    public static void tracePathsToDatabase(CallGraph callGraph, String entryPoint, String databaseMethod) {
        System.out.println("=== Tracing paths from " + entryPoint + " to " + databaseMethod + " ===");
        
        List<List<String>> chains = callGraph.findCallChainsToTarget(entryPoint, databaseMethod, 10);
        
        if (chains.isEmpty()) {
            System.out.println("No paths found - database method is not reachable!");
        } else {
            System.out.println("Found " + chains.size() + " paths to database:");
            for (List<String> chain : chains) {
                System.out.println("  " + CallGraph.formatCallChain(chain));
            }
        }
    }
    
    /**
     * Example 3: Analyze transaction boundaries.
     * Use case: Find all methods called within a transaction scope.
     */
    public static void analyzeTransactionBoundaries(CallGraph callGraph, SourceModel sourceModel) {
        System.out.println("=== Analyzing Transaction Boundaries ===");
        
        // Find all @Transactional methods
        Set<String> transactionalMethods = sourceModel.getMethods().values().stream()
                .filter(Method::isTransactional)
                .map(Method::getFullyQualifiedName)
                .collect(Collectors.toSet());
        
        System.out.println("Found " + transactionalMethods.size() + " @Transactional methods");
        
        // Trace call chains from each transactional method
        Map<String, List<List<String>>> txBoundaries = callGraph.findTransactionBoundaries(transactionalMethods, 5);
        
        for (Map.Entry<String, List<List<String>>> entry : txBoundaries.entrySet()) {
            String txMethod = entry.getKey();
            List<List<String>> chains = entry.getValue();
            
            System.out.println("\n@Transactional method: " + txMethod);
            System.out.println("  Execution paths (" + chains.size() + " total):");
            
            // Show first 3 chains as examples
            for (int i = 0; i < Math.min(3, chains.size()); i++) {
                System.out.println("    " + CallGraph.formatCallChain(chains.get(i)));
            }
            if (chains.size() > 3) {
                System.out.println("    ... and " + (chains.size() - 3) + " more paths");
            }
        }
    }
    
    /**
     * Example 4: Detect transaction boundary violations.
     * Use case: Find non-transactional methods calling transactional methods.
     */
    public static void detectTransactionViolations(CallGraph callGraph, SourceModel sourceModel) {
        System.out.println("=== Detecting Transaction Boundary Violations ===");
        
        // Find all @Transactional methods
        Set<String> transactionalMethods = sourceModel.getMethods().values().stream()
                .filter(Method::isTransactional)
                .map(Method::getFullyQualifiedName)
                .collect(Collectors.toSet());
        
        // Find all non-transactional methods
        Set<String> nonTransactionalMethods = sourceModel.getMethods().values().stream()
                .filter(m -> !m.isTransactional())
                .map(Method::getFullyQualifiedName)
                .collect(Collectors.toSet());
        
        int violations = 0;
        
        for (String nonTxMethod : nonTransactionalMethods) {
            for (String txMethod : transactionalMethods) {
                // Check if non-transactional method calls transactional method
                List<List<String>> paths = callGraph.findCallChainsToTarget(nonTxMethod, txMethod, 3);
                
                if (!paths.isEmpty()) {
                    violations++;
                    System.out.println("\n⚠️  Violation: Non-transactional method calls @Transactional method");
                    System.out.println("  Path: " + CallGraph.formatCallChain(paths.get(0)));
                }
            }
        }
        
        if (violations == 0) {
            System.out.println("✅ No transaction boundary violations found!");
        } else {
            System.out.println("\n❌ Found " + violations + " transaction boundary violations");
        }
    }
    
    /**
     * Example 5: Find the longest call chain (deepest execution path).
     * Use case: Identify complex execution flows that might need refactoring.
     */
    public static void findLongestCallChain(CallGraph callGraph, String startMethod) {
        System.out.println("=== Finding longest call chain from: " + startMethod + " ===");
        
        List<List<String>> chains = callGraph.findCallChains(startMethod, 10);
        
        if (chains.isEmpty()) {
            System.out.println("No call chains found");
            return;
        }
        
        // Find the longest chain
        List<String> longestChain = chains.stream()
                .max((c1, c2) -> Integer.compare(c1.size(), c2.size()))
                .orElse(List.of());
        
        System.out.println("Longest chain has " + longestChain.size() + " methods:");
        System.out.println(CallGraph.formatCallChain(longestChain));
        
        // Show statistics
        double avgLength = chains.stream()
                .mapToInt(List::size)
                .average()
                .orElse(0.0);
        
        System.out.println("\nStatistics:");
        System.out.println("  Total paths: " + chains.size());
        System.out.println("  Average depth: " + String.format("%.1f", avgLength));
        System.out.println("  Max depth: " + longestChain.size());
    }
}
