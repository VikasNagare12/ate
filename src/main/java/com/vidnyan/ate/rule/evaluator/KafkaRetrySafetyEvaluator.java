package com.vidnyan.ate.rule.evaluator;

import com.vidnyan.ate.graph.CallGraph;
import com.vidnyan.ate.graph.DependencyGraph;
import com.vidnyan.ate.model.Method;
import com.vidnyan.ate.model.SourceModel;
import com.vidnyan.ate.rule.RuleDefinition;
import com.vidnyan.ate.rule.Violation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;

@Slf4j
@Component
public class KafkaRetrySafetyEvaluator implements RuleEvaluator {

    private static final String ID = "KAFKA-RETRY-SAFETY-001";
    
    // Kafka Template FQN
    private static final String KAFKA_TEMPLATE = "org.springframework.kafka.core.KafkaTemplate";
    private static final String SEND_METHOD = "send";

    // Annotations to forbid
    private static final Set<String> RETRY_ANNOTATIONS = Set.of(
            "Retry",         // Resilience4j
            "Retryable",     // Spring Retry
            "io.github.resilience4j.retry.annotation.Retry",
            "org.springframework.retry.annotation.Retryable"
    );

    @Override
    public boolean isApplicable(RuleDefinition rule) {
        return ID.equals(rule.getId());
    }

    @Override
    public List<Violation> detectViolations(RuleDefinition rule, SourceModel sourceModel, CallGraph callGraph, DependencyGraph dependencyGraph) {
        List<Violation> violations = new ArrayList<>();

        // 1. Find all calls to KafkaTemplate.send*
        Set<String> kafkaSendCallers = findKafkaSendCallers(sourceModel, callGraph);
        
        for (String caller : kafkaSendCallers) {
             // 2. Traverse Upstream
             List<List<String>> chains = findUpstreamChains(caller, callGraph);
             
             for (List<String> chain : chains) {
                 // Check chain for Retry annotations
                 // Chain: [Bottom (caller), ..., Top (Root)]
                 
                 for (String methodSig : chain) {
                     Method method = sourceModel.getMethod(methodSig);
                     if (method != null) {
                         boolean isRetried = RETRY_ANNOTATIONS.stream()
                                 .anyMatch(ann -> method.hasAnnotation(ann));
                         
                         if (isRetried) {
                             // Violation Found!
                             violations.add(createViolation(rule, chain, methodSig));
                             // Don't need to report same chain multiple times if multiple retries exist
                             break; 
                         }
                     }
                 }
             }
        }

        return violations;
    }

    private Set<String> findKafkaSendCallers(SourceModel sourceModel, CallGraph callGraph) {
        Set<String> callers = new HashSet<>();
        for (Method method : sourceModel.getMethods().values()) {
            List<String> callees = callGraph.getCallees(method.getFullyQualifiedName());
            for (String callee : callees) {
                // Check if callee is KafkaTemplate.send*
                // Callee FQN: package.Class#method(params)
                if (callee.startsWith(KAFKA_TEMPLATE + "#" + SEND_METHOD)) {
                    callers.add(method.getFullyQualifiedName());
                }
            }
        }
        return callers;
    }

    private List<List<String>> findUpstreamChains(String startNode, CallGraph callGraph) {
        List<List<String>> result = new ArrayList<>();
        // 10 is the depth limit required
        findUpstreamRecursive(startNode, new ArrayList<>(), new HashSet<>(), result, callGraph, 0);
        return result;
    }

    private void findUpstreamRecursive(String currentMethod, List<String> currentChain, Set<String> visited, 
                                       List<List<String>> result, CallGraph callGraph, int depth) {
        if (depth > 10) { 
             List<String> fullChain = new ArrayList<>(currentChain);
             fullChain.add(currentMethod);
             result.add(fullChain);
             return;
        }

        if (visited.contains(currentMethod)) {
            return;
        }

        currentChain.add(currentMethod);
        visited.add(currentMethod);

        List<String> callers = callGraph.getCallers(currentMethod);
        
        if (callers.isEmpty()) {
            result.add(new ArrayList<>(currentChain));
        } else {
            for (String caller : callers) {
                findUpstreamRecursive(caller, currentChain, visited, result, callGraph, depth + 1);
            }
        }

        visited.remove(currentMethod);
        currentChain.remove(currentChain.size() - 1);
    }

    private Violation createViolation(RuleDefinition rule, List<String> chain, String retryMethod) {
        String method = chain.isEmpty() ? "Unknown" : chain.get(0);
        
        // Format chain: Top -> Bottom
        List<String> displayChain = new ArrayList<>(chain);
        Collections.reverse(displayChain);
        String chainStr = String.join(" -> ", displayChain);

        return Violation.builder()
                .ruleId(rule.getId())
                .severity(rule.getSeverity())
                .message(String.format("KafkaTemplate.send() is called within a retry boundary at '%s'. Chain: %s", retryMethod, chainStr))
                .location(null)
                .context(Map.of(
                    "elementId", method,
                    "retryMethod", retryMethod,
                    "callChain", chainStr
                ))
                .fingerprint(Violation.generateFingerprint(ID, null, Map.of("chain", chainStr)))
                .build();
    }
}
