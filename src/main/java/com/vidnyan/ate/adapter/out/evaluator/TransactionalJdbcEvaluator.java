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
 * Detects JDBC operations executed without an active transaction.
 * All DB operations should be inside a @Transactional method/class.
 */
@Slf4j
@Component
public class TransactionalJdbcEvaluator implements RuleEvaluator {

    private static final Set<String> JDBC_TYPES = Set.of(
            "org.springframework.jdbc.core.JdbcTemplate",
            "org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate",
            "org.springframework.data.jpa.repository.JpaRepository",
            "javax.persistence.EntityManager",
            "jakarta.persistence.EntityManager"
    );

    @Override
    public boolean supports(RuleDefinition rule) {
        return "JDBC-TX-001".equals(rule.id());
    }

    @Override
    public EvaluationResult evaluate(EvaluationContext context) {
        Instant start = Instant.now();
        SourceModel model = context.sourceModel();
        CallGraph callGraph = context.callGraph();
        RuleDefinition rule = context.rule();

        log.debug("Evaluating JDBC transaction safety...");

        List<Violation> violations = new ArrayList<>();
        int pathsAnalyzed = 0;

        // 1. Identify all application 'roots'
        Set<String> roots = findRootMethods(callGraph);
        log.debug("Found {} application root methods", roots.size());

        // 2. Identify all methods that directly call JDBC sinks
        Set<String> applicationSinks = findApplicationSinks(callGraph);
        log.debug("Found {} application methods calling JDBC directly", applicationSinks.size());

        // 3. Check paths
        for (String sinkFqn : applicationSinks) {
            for (String rootFqn : roots) {
                // Find all paths from root to sink
                List<List<String>> chains = callGraph.findCallChains(rootFqn, sinkFqn);

                for (List<String> chain : chains) {
                    pathsAnalyzed++;

                    // Check if *any* method in the chain has @Transactional
                    boolean hasTransaction = chain.stream()
                            .anyMatch(fqn -> hasTransactionalAnnotation(fqn, model));

                    if (!hasTransaction) {
                        model.getMethod(rootFqn).ifPresent(rootMethod -> {
                            violations.add(Violation.builder()
                                    .ruleId(rule.id())
                                    .ruleName(rule.name())
                                    .severity(rule.severity())
                                    .message(String.format(
                                            "JDBC operation via '%s' is reachable from '%s' without any @Transactional context. " +
                                                    "This can lead to partial failures and data inconsistency.",
                                            sinkFqn.substring(sinkFqn.lastIndexOf('.') + 1),
                                            rootMethod.simpleName()))
                                    .location(rootMethod.location())
                                    .callChain(chain)
                                    .build());
                        });
                    }
                }
            }
        }

        Duration duration = Duration.between(start, Instant.now());
        log.debug("Rule {} found {} violations in {}ms", rule.id(), violations.size(), duration.toMillis());
        
        return EvaluationResult.success(rule.id(), violations, duration, pathsAnalyzed);
    }

    private Set<String> findRootMethods(CallGraph callGraph) {
        Set<String> roots = new HashSet<>();
        for (String methodFqn : callGraph.getApplicationMethods()) {
            boolean hasOtherAppCallers = callGraph.getIncomingCalls(methodFqn).stream()
                    .anyMatch(edge -> callGraph.isApplicationMethod(edge.callerFqn())
                            && !edge.callerFqn().equals(methodFqn)); // Ignore self-recursion

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
                boolean callsJdbcWrite = callGraph.getOutgoingCalls(callerFqn).stream()
                        .anyMatch(edge -> {
                            String callee = edge.effectiveCalleeFqn();
                            if (callee == null)
                                return false;

                            boolean isJdbc = JDBC_TYPES.stream().anyMatch(callee::startsWith);
                            if (!isJdbc)
                                return false;

                            // Check if it is a write operation
                            return isWriteOperation(callee);
                        });

                if (callsJdbcWrite) {
                    sinks.add(callerFqn);
                }
            }
        }
        return sinks;
    }

    private boolean isWriteOperation(String calleeFqn) {
        // Extract method name
        int hash = calleeFqn.indexOf('#');
        // Fallback if no hash present (should vary by format, but usually Type#method
        // or Type.method)
        // Adjusting for common FQN formats if necessary, but standard here implies
        // keeping simple.
        // Assuming format is Type.method or Type#method. Let's try to find the last dot
        // or hash.

        String methodName;
        if (hash > 0) {
            methodName = calleeFqn.substring(hash + 1);
        } else {
            methodName = calleeFqn.substring(calleeFqn.lastIndexOf('.') + 1);
        }

        return methodName.startsWith("update") ||
                methodName.startsWith("batchUpdate") ||
                methodName.startsWith("insert") ||
                methodName.startsWith("save") ||
                methodName.startsWith("delete") ||
                methodName.startsWith("persist") ||
                methodName.startsWith("merge") ||
                methodName.startsWith("remove");
    }

    private boolean hasTransactionalAnnotation(String methodFqn, SourceModel model) {
        return model.getMethod(methodFqn).map(method -> {
            // Check method level
            if (method.hasAnnotation("Transactional")) return true;
            
            // Check class level
            return model.getType(method.containingTypeFqn())
                    .map(t -> t.hasAnnotation("Transactional"))
                    .orElse(false);
        }).orElse(false);
    }
}
