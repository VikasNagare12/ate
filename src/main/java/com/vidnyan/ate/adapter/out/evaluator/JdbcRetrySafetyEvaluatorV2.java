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
 * Detects JDBC operations without retry mechanism.
 * JDBC calls can fail due to transient errors and should have retry logic.
 */
@Slf4j
@Component

public class JdbcRetrySafetyEvaluatorV2 implements RuleEvaluator {
    
    private static final Set<String> JDBC_TYPES = Set.of(
            "org.springframework.jdbc.core.JdbcTemplate",
            "org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate",
            "org.springframework.data.jpa.repository.JpaRepository",
            "javax.persistence.EntityManager",
            "jakarta.persistence.EntityManager"
    );
    
    private static final Set<String> RETRY_ANNOTATIONS = Set.of(
            "Retryable", "Retry", "CircuitBreaker"
    );
    
    @Override
    public boolean supports(RuleDefinition rule) {
        return "JDBC-RETRY-001".equals(rule.id()) 
                || RuleDefinition.Category.RETRY_SAFETY == rule.category();
    }
    
    @Override
    public EvaluationResult evaluate(EvaluationContext context) {
        Instant start = Instant.now();
        SourceModel model = context.sourceModel();
        CallGraph callGraph = context.callGraph();
        RuleDefinition rule = context.rule();
        
        log.debug("Evaluating JDBC retry safety");
        
        List<Violation> violations = new ArrayList<>();
        int nodesAnalyzed = 0;
        
        // Find all methods that call JDBC
        Set<String> jdbcCallers = findJdbcCallingMethods(model, callGraph);
        log.debug("Found {} methods calling JDBC", jdbcCallers.size());
        
        for (String methodFqn : jdbcCallers) {
            nodesAnalyzed++;
            
            model.getMethod(methodFqn).ifPresent(method -> {
                // Check if method or its call chain has retry
                if (!hasRetryAnnotation(method) && !hasRetryInCallChain(methodFqn, model, callGraph)) {
                    violations.add(Violation.builder()
                            .ruleId(rule.id())
                            .ruleName(rule.name())
                            .severity(rule.severity())
                            .message(String.format(
                                    "Method '%s' calls JDBC without retry mechanism. " +
                                    "Add @Retryable to handle transient database errors.",
                                    method.simpleName()
                            ))
                            .location(method.location())
                            .callChain(List.of(methodFqn))
                            .build());
                }
            });
        }
        
        Duration duration = Duration.between(start, Instant.now());
        return EvaluationResult.success(rule.id(), violations, duration, nodesAnalyzed);
    }
    
    private Set<String> findJdbcCallingMethods(SourceModel model, CallGraph callGraph) {
        Set<String> callers = new HashSet<>();
        
        for (MethodEntity method : model.methods().values()) {
            for (CallEdge edge : callGraph.getOutgoingCalls(method.fullyQualifiedName())) {
                String callee = edge.effectiveCalleeFqn();
                if (callee != null && isJdbcCall(callee)) {
                    callers.add(method.fullyQualifiedName());
                    break;
                }
            }
        }
        
        return callers;
    }
    
    private boolean isJdbcCall(String calleeFqn) {
        for (String jdbcType : JDBC_TYPES) {
            if (calleeFqn.startsWith(jdbcType + "#")) {
                return true;
            }
        }
        return false;
    }
    
    private boolean hasRetryAnnotation(MethodEntity method) {
        for (String retryAnnotation : RETRY_ANNOTATIONS) {
            if (method.hasAnnotation(retryAnnotation)) {
                return true;
            }
        }
        return false;
    }
    
    private boolean hasRetryInCallChain(String methodFqn, SourceModel model, CallGraph callGraph) {
        // Check if any caller in the chain has retry annotation
        Set<String> visited = new HashSet<>();
        return hasRetryInCallersRecursive(methodFqn, model, callGraph, visited, 0);
    }
    
    private boolean hasRetryInCallersRecursive(
            String methodFqn,
            SourceModel model,
            CallGraph callGraph,
            Set<String> visited,
            int depth
    ) {
        if (depth > 10 || visited.contains(methodFqn)) {
            return false;
        }
        visited.add(methodFqn);
        
        for (String caller : callGraph.getCallers(methodFqn)) {
            MethodEntity callerMethod = model.getMethod(caller).orElse(null);
            if (callerMethod != null && hasRetryAnnotation(callerMethod)) {
                return true;
            }
            
            if (hasRetryInCallersRecursive(caller, model, callGraph, visited, depth + 1)) {
                return true;
            }
        }
        
        return false;
    }
}
