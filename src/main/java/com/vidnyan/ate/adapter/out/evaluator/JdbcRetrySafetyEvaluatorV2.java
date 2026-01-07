package com.vidnyan.ate.adapter.out.evaluator;

import com.vidnyan.ate.domain.graph.CallGraph;
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
        
        log.debug("Evaluating JDBC retry safety using async-aware path analysis");
        
        List<Violation> violations = new ArrayList<>();
        
        // 1. Identify all application 'roots'
        Set<String> roots = findRootMethods(callGraph);
        log.debug("Found {} application root methods", roots.size());
        
        // 2. Identify all methods that directly call JDBC sinks
        Set<String> applicationSinks = findApplicationSinks(callGraph);
        log.debug("Found {} application methods calling JDBC directly", applicationSinks.size());

        int pathsAnalyzed = 0;

        // 3. Build and check all possible graphs (paths) from roots to sinks
        for (String sinkFqn : applicationSinks) {
            for (String rootFqn : roots) {
                List<List<String>> chains = callGraph.findCallChains(rootFqn, sinkFqn);

                for (List<String> chain : chains) {
                    pathsAnalyzed++;

                    // Identify the 'last' Async boundary in the chain
                    int asyncIndex = findLastAsyncIndex(chain, model);

                    // The 'safety window' starts from the last @Async method (or 0 if none)
                    // Retry annotations BEFORE this index are ignored because they can't catch
                    // async failures
                    List<String> safetyWindow = chain.subList(Math.max(0, asyncIndex), chain.size());

                    boolean protectedPath = safetyWindow.stream()
                            .anyMatch(fqn -> hasRetryAnnotation(fqn, model));

                    if (!protectedPath) {
                        model.getMethod(rootFqn).ifPresent(rootMethod -> {
                            String messagePrefix = asyncIndex >= 0
                                    ? String.format("Unprotected JDBC call path after @Async boundary in '%s'",
                                            chain.get(asyncIndex).substring(chain.get(asyncIndex).lastIndexOf('#') + 1))
                                    : String.format("Unprotected JDBC call path from root '%s'",
                                            rootMethod.simpleName());

                            violations.add(Violation.builder()
                                    .ruleId(rule.id())
                                    .ruleName(rule.name())
                                    .severity(rule.severity())
                                    .message(String.format(
                                            "%s to database operation via '%s'. " +
                                                    "At least one method in the chain (after the last thread switch) should have @Retryable.",
                                            messagePrefix,
                                            sinkFqn.substring(sinkFqn.lastIndexOf('#') + 1)))
                                    .location(rootMethod.location())
                                    .callChain(chain)
                                    .build());
                        });
                    }
                }
            }
        }
        
        Duration duration = Duration.between(start, Instant.now());
        return EvaluationResult.success(rule.id(), violations, duration, pathsAnalyzed);
    }

    private int findLastAsyncIndex(List<String> chain, SourceModel model) {
        for (int i = chain.size() - 1; i >= 0; i--) {
            String fqn = chain.get(i);
            if (model.getMethod(fqn).map(m -> m.hasAnnotation("Async")).orElse(false)) {
                return i;
            }
        }
        return -1;
    }

    private Set<String> findRootMethods(CallGraph callGraph) {
        Set<String> roots = new HashSet<>();
        for (String methodFqn : callGraph.getApplicationMethods()) {
            boolean hasOtherAppCallers = callGraph.getIncomingCalls(methodFqn).stream()
                    .anyMatch(edge -> callGraph.isApplicationMethod(edge.callerFqn())
                            && !edge.callerFqn().equals(methodFqn));

            if (!hasOtherAppCallers) {
                roots.add(methodFqn);
            }
        }
        return roots;
    }

    private Set<String> findApplicationSinks(CallGraph callGraph) {
        Set<String> sinks = new HashSet<>();
        for (String callerFqn : callGraph.getMethodsWithOutgoingCalls()) {
            if (callGraph.isApplicationMethod(callerFqn)) {
                boolean callsJdbc = callGraph.getOutgoingCalls(callerFqn).stream()
                        .anyMatch(edge -> {
                            String callee = edge.effectiveCalleeFqn();
                            return callee != null && JDBC_TYPES.stream().anyMatch(callee::startsWith);
                        });

                if (callsJdbc) {
                    sinks.add(callerFqn);
                }
            }
        }
        return sinks;
    }

    private boolean hasRetryAnnotation(String methodFqn, SourceModel model) {
        return model.getMethod(methodFqn).map(method -> {
            if (RETRY_ANNOTATIONS.stream().anyMatch(method::hasAnnotation)) {
                return true;
            }
            return model.getType(method.containingTypeFqn())
                    .map(t -> RETRY_ANNOTATIONS.stream().anyMatch(t::hasAnnotation))
                    .orElse(false);
        }).orElse(false);
    }
}
