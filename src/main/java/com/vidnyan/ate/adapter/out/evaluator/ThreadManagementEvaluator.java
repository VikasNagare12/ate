package com.vidnyan.ate.adapter.out.evaluator;

import com.vidnyan.ate.domain.graph.CallEdge;
import com.vidnyan.ate.domain.graph.CallGraph;
import com.vidnyan.ate.domain.model.MethodEntity;
import com.vidnyan.ate.domain.model.SourceModel;
import com.vidnyan.ate.domain.rule.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Detects direct Thread creation instead of using Spring executors.
 * Direct Thread creation bypasses Spring's thread management.
 */
@Slf4j
@Component
@Order(15)
public class ThreadManagementEvaluator implements RuleEvaluator {
    
    private static final Set<String> THREAD_CREATION_PATTERNS = Set.of(
            "java.lang.Thread#<init>",
            "java.lang.Thread#start"
    );
    
    private static final Set<String> EXECUTOR_PATTERNS = Set.of(
            "java.util.concurrent.ExecutorService",
            "java.util.concurrent.Executor",
            "org.springframework.core.task.TaskExecutor",
            "org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor"
    );
    
    @Override
    public boolean supports(RuleDefinition rule) {
        return "THREAD-MGMT-001".equals(rule.id());
    }
    
    @Override
    public EvaluationResult evaluate(EvaluationContext context) {
        Instant start = Instant.now();
        SourceModel model = context.sourceModel();
        CallGraph callGraph = context.callGraph();
        RuleDefinition rule = context.rule();
        
        log.debug("Evaluating direct Thread creation");
        
        List<Violation> violations = new ArrayList<>();
        int nodesAnalyzed = 0;
        
        // Check all methods for direct Thread usage
        for (MethodEntity method : model.methods().values()) {
            nodesAnalyzed++;
            
            for (CallEdge edge : callGraph.getOutgoingCalls(method.fullyQualifiedName())) {
                String callee = edge.effectiveCalleeFqn();
                if (callee == null) continue;
                
                // Check for direct Thread creation
                if (isDirectThreadCreation(callee)) {
                    violations.add(Violation.builder()
                            .ruleId(rule.id())
                            .ruleName(rule.name())
                            .severity(rule.severity())
                            .message(String.format(
                                    "Method '%s' creates Thread directly. " +
                                    "Use @Async or TaskExecutor/ThreadPoolTaskExecutor instead.",
                                    method.simpleName()
                            ))
                            .location(edge.location())
                            .callChain(List.of(method.fullyQualifiedName(), callee))
                            .build());
                }
            }
        }
        
        Duration duration = Duration.between(start, Instant.now());
        return EvaluationResult.success(rule.id(), violations, duration, nodesAnalyzed);
    }
    
    private boolean isDirectThreadCreation(String calleeFqn) {
        for (String pattern : THREAD_CREATION_PATTERNS) {
            if (calleeFqn.startsWith(pattern) || calleeFqn.contains("Thread#<init>") 
                    || calleeFqn.contains("Thread#start")) {
                return true;
            }
        }
        
        // Also check for new Thread() constructor calls
        if (calleeFqn.contains("java.lang.Thread")) {
            return true;
        }
        
        return false;
    }
}
