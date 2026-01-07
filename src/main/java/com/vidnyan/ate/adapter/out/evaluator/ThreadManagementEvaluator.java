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
 * Detects direct Thread creation instead of using Spring executors.
 * Direct Thread creation bypasses Spring's thread management.
 */
@Slf4j
@Component
public class ThreadManagementEvaluator implements RuleEvaluator {
    
    private static final Set<String> THREAD_CREATION_PATTERNS = Set.of(
            "java.lang.Thread#<init>",
            "java.lang.Thread#<init>()",
            "java.lang.Thread#start",
            "java.lang.Thread#start()"
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
        
        log.info("ThreadManagementEvaluator: Evaluating rule {}", rule.id());
        
        List<Violation> violations = new ArrayList<>();
        int nodesAnalyzed = 0;
        
        // Check all methods for direct Thread usage
        for (MethodEntity method : model.methods().values()) {
            nodesAnalyzed++;
            
            for (CallEdge edge : callGraph.getOutgoingCalls(method.fullyQualifiedName())) {
                // Log all constructor calls for debugging
                if (edge.callType() == CallEdge.CallType.CONSTRUCTOR) {
                    log.info("  Constructor: raw='{}', resolved='{}'",
                            edge.calleeFqn(), edge.resolvedCalleeFqn());
                }
                
                // Check for direct Thread creation
                if (isDirectThreadCreation(edge)) {
                    log.info("  VIOLATION: Thread creation in {}", method.simpleName());
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
                            .callChain(List.of(method.fullyQualifiedName(), edge.effectiveCalleeFqn()))
                            .build());
                }
            }
        }
        
        log.info("ThreadManagementEvaluator: Found {} violations in {} methods",
                violations.size(), nodesAnalyzed);

        Duration duration = Duration.between(start, Instant.now());
        return EvaluationResult.success(rule.id(), violations, duration, nodesAnalyzed);
    }
    
    private boolean isDirectThreadCreation(CallEdge edge) {
        String resolved = edge.resolvedCalleeFqn();
        String raw = edge.calleeFqn();

        // Check resolved FQN
        if (resolved != null && THREAD_CREATION_PATTERNS.contains(resolved)) {
            log.debug("Found Thread creation (resolved): {}", resolved);
                return true;
        }
        
        // Check raw callee (for unresolved cases)
        if (raw != null && raw.contains("new Thread") || raw.startsWith("Thread.")) {
            log.debug("Found Thread creation (raw): {}", raw);
            return true;
            }
        return false;
    }
}
