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
import java.util.List;
import java.util.Set;

/**
 * Evaluates transaction boundary violations.
 * Detects remote calls (HTTP, messaging) made inside @Transactional methods.
 */
@Slf4j
@Component

public class TransactionBoundaryEvaluatorV2 implements RuleEvaluator {
    
    private static final String TRANSACTIONAL = "Transactional";
    private static final String TRANSACTIONAL_FQN = "org.springframework.transaction.annotation.Transactional";
    
    private static final Set<String> REMOTE_CALL_TYPES = Set.of(
            "org.springframework.web.client.RestTemplate",
            "org.springframework.web.reactive.function.client.WebClient",
            "org.springframework.kafka.core.KafkaTemplate",
            "org.apache.kafka.clients.producer.KafkaProducer",
            "java.net.http.HttpClient",
            "org.apache.http.client.HttpClient",
            "feign.Client"
    );
    
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
        
        log.debug("Evaluating transaction boundary violations");
        
        List<Violation> violations = new ArrayList<>();
        int nodesAnalyzed = 0;
        
        // Find all @Transactional methods
        List<MethodEntity> transactionalMethods = model.findMethodsWithAnnotation(TRANSACTIONAL);
        log.debug("Found {} @Transactional methods", transactionalMethods.size());
        
        for (MethodEntity method : transactionalMethods) {
            nodesAnalyzed++;
            
            // Check if any remote call is reachable
            List<CallEdge> remoteCalls = findRemoteCalls(method.fullyQualifiedName(), callGraph);
            
            for (CallEdge remoteCall : remoteCalls) {
                String remoteType = extractType(remoteCall.effectiveCalleeFqn());
                
                violations.add(Violation.builder()
                        .ruleId(rule.id())
                        .ruleName(rule.name())
                        .severity(rule.severity())
                        .message(String.format(
                                "@Transactional method '%s' makes remote call to %s. " +
                                "This can hold database connections if the remote service is slow.",
                                method.simpleName(),
                                remoteType
                        ))
                        .location(method.location())
                        .callChain(List.of(method.fullyQualifiedName(), remoteCall.effectiveCalleeFqn()))
                        .build());
            }
        }
        
        Duration duration = Duration.between(start, Instant.now());
        return EvaluationResult.success(rule.id(), violations, duration, nodesAnalyzed);
    }
    
    private List<CallEdge> findRemoteCalls(String methodFqn, CallGraph callGraph) {
        List<CallEdge> remoteCalls = new ArrayList<>();
        findRemoteCallsRecursive(methodFqn, callGraph, remoteCalls, new java.util.HashSet<>(), 0);
        return remoteCalls;
    }
    
    private void findRemoteCallsRecursive(
            String current,
            CallGraph callGraph,
            List<CallEdge> found,
            Set<String> visited,
            int depth
    ) {
        if (depth > 30 || visited.contains(current)) {
            return;
        }
        visited.add(current);
        
        for (CallEdge edge : callGraph.getOutgoingCalls(current)) {
            String callee = edge.effectiveCalleeFqn();
            if (callee == null) continue;
            
            // Check if this is a remote call
            if (isRemoteCall(callee)) {
                found.add(edge);
            }
            
            // Continue searching only in application code
            if (callGraph.isApplicationMethod(callee)) {
                findRemoteCallsRecursive(callee, callGraph, found, visited, depth + 1);
            }
        }
    }
    
    private boolean isRemoteCall(String calleeFqn) {
        for (String remoteType : REMOTE_CALL_TYPES) {
            if (calleeFqn.startsWith(remoteType + "#")) {
                return true;
            }
        }
        return false;
    }
    
    private String extractType(String fqn) {
        if (fqn == null) return "unknown";
        int hash = fqn.indexOf('#');
        String type = hash > 0 ? fqn.substring(0, hash) : fqn;
        int lastDot = type.lastIndexOf('.');
        return lastDot > 0 ? type.substring(lastDot + 1) : type;
    }
}
