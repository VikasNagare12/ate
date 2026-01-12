package com.vidnyan.ate.adapter.out.evaluator;

import com.vidnyan.ate.domain.graph.CallEdge;
import com.vidnyan.ate.domain.graph.CallGraph;
import com.vidnyan.ate.domain.model.MethodEntity;
import com.vidnyan.ate.domain.model.SourceModel;
import com.vidnyan.ate.domain.rule.*;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Evaluates transaction boundary violations.
 * Detects remote calls (HTTP, messaging) made inside @Transactional methods.
 * 
 * Replaces generic PathReachabilityEvaluator with specific implementation
 * that still reads configuration from JSON.
 */
@Slf4j
@Component
public class TransactionBoundaryEvaluatorV2 implements RuleEvaluator {
    
    private static final String TRANSACTIONAL = "Transactional";
    
    @Override
    public boolean supports(RuleDefinition rule) {
        return "TX-BOUNDARY-001".equals(rule.id());
    }
    
    @Override
    public EvaluationResult evaluate(EvaluationContext context) {
        Instant start = Instant.now();
        SourceModel model = context.sourceModel();
        CallGraph callGraph = context.callGraph();
        RuleDefinition rule = context.rule();
        
        log.debug("Evaluating rule {} with TransactionBoundaryEvaluatorV2", rule.id());
        
        List<Violation> violations = new ArrayList<>();
        int nodesAnalyzed = 0;
        
        // 1. Identify Entry Points (@Transactional methods)
        List<MethodEntity> transactionalMethods = model.findMethodsWithAnnotation(TRANSACTIONAL);
        log.debug("Found {} @Transactional methods", transactionalMethods.size());
        
        // 2. Identify Sinks (from JSON configuration)
        Set<String> sinkTypes = new HashSet<>(rule.detection().sinks().types());

        // 3. Analyze each entry point
        for (MethodEntity method : transactionalMethods) {
            nodesAnalyzed++;
            
            // Find call chains to any method belonging to a sink type
            List<List<String>> chains = findChainsToSinks(
                    method.fullyQualifiedName(),
                    sinkTypes,
                    callGraph);
            
            for (List<String> chain : chains) {
                // The last element in the chain is the sink method
                String sinkMethod = chain.get(chain.size() - 1);
                String sinkType = extractType(sinkMethod);
                
                violations.add(Violation.builder()
                        .ruleId(rule.id())
                        .ruleName(rule.name())
                        .severity(rule.severity())
                        .message(String.format(
                                "@Transactional method '%s' calls %s which violates rule: %s",
                                        method.simpleName(),
                                sinkType,
                                rule.name()
                        ))
                        .location(method.location())
                        .callChain(chain)
                        .build());
            }
        }
        
        Duration duration = Duration.between(start, Instant.now());
        log.debug("Rule {} found {} violations in {}ms", rule.id(), violations.size(), duration.toMillis());

        return EvaluationResult.success(rule.id(), violations, duration, nodesAnalyzed);
    }
    
    private List<List<String>> findChainsToSinks(
            String startMethod,
            Set<String> sinkTypes,
            CallGraph callGraph) {
        List<List<String>> allChains = new ArrayList<>();
        // Use recursive search with depth limit (similar to
        // CallGraph.findChainsRecursive but with pattern matching)
        findChainsToSinkRecursive(
                startMethod,
                sinkTypes,
                new ArrayList<>(),
                new HashSet<>(),
                allChains,
                0,
                callGraph);
        return allChains;
    }
    
    private void findChainsToSinkRecursive(
            String current,
            Set<String> sinkTypes,
                    List<String> chain,
            Set<String> visited,
            List<List<String>> allChains,
            int depth,
            CallGraph callGraph
    ) {
        // Max depth to prevent stack overflow and performance issues
        if (depth > 50 || visited.contains(current)) {
            return;
        }

        chain.add(current);
        visited.add(current);
        
        // Check if current method belongs to a sink type
        if (isSink(current, sinkTypes)) {
            allChains.add(new ArrayList<>(chain));
        } else if (callGraph.isApplicationMethod(current)) {
            // Only traverse outgoing calls if we are still in application code
            // (Don't traverse INTO library code looking for other libraries, we stop at the
            // boundary)
            for (CallEdge edge : callGraph.getOutgoingCalls(current)) {
                String callee = edge.effectiveCalleeFqn();
                if (callee != null) {
                    findChainsToSinkRecursive(callee, sinkTypes, chain, visited, allChains, depth + 1, callGraph);
                }
            }
        }

        chain.remove(chain.size() - 1);
        visited.remove(current);
    }
    
    private boolean isSink(String methodFqn, Set<String> sinkTypes) {
        for (String type : sinkTypes) {
            if (methodFqn.startsWith(type + "#") || methodFqn.startsWith(type + ".")) {
                return true;
            }
        }
        return false;
    }
    
    private String extractType(String methodFqn) {
        if (methodFqn.contains("#")) {
            String fqn = methodFqn.substring(0, methodFqn.indexOf('#'));
            return fqn.substring(fqn.lastIndexOf('.') + 1);
        }
        return "Unknown";
    }
}
