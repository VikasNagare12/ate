package com.vidnyan.ate.rule.evaluator;

import com.vidnyan.ate.graph.CallGraph;
import com.vidnyan.ate.graph.DependencyGraph;
import com.vidnyan.ate.model.Method;
import com.vidnyan.ate.model.SourceModel;
import com.vidnyan.ate.rule.RuleDefinition;
import com.vidnyan.ate.rule.Violation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Enforces rule: @Transactional methods should not call @Async methods.
 * (TRANSACTIONAL_CALLS_ASYNC)
 * <p>
 * Async execution happens in a separate thread, meaning the transaction context
 * is lost. If the async task fails, the transaction will not roll back,
 * leading to partial commits or inconsistency.
 * </p>
 */
@Slf4j
@Component
public class TransactionalCallsAsyncEvaluator implements RuleEvaluator {

    private static final String ID = "TRANSACTIONAL-CALLS-ASYNC"; // Standardized ID
    private static final String ID_LEGACY = "TRANSACTIONAL_CALLS_ASYNC"; // Handle legacy ID if needed or just enforcement
    
    private static final String TRANSACTIONAL = "Transactional";
    private static final String ASYNC = "Async";

    @Override
    public boolean isApplicable(RuleDefinition rule) {
        return ID.equals(rule.getId()) || ID_LEGACY.equals(rule.getId());
    }

    @Override
    public List<Violation> detectViolations(RuleDefinition rule, SourceModel sourceModel, CallGraph callGraph, DependencyGraph dependencyGraph) {
        List<Violation> violations = new ArrayList<>();
        
        List<Method> txMethods = sourceModel.getMethodsAnnotatedWith(TRANSACTIONAL);

        for (Method txMethod : txMethods) {
            Set<String> reachable = callGraph.findReachableMethods(txMethod.getFullyQualifiedName());
            
            for (String targetName : reachable) {
                Method target = sourceModel.getMethod(targetName);
                if (target != null && target.hasAnnotation(ASYNC)) {
                    
                     List<List<String>> chains = callGraph.findCallChainsToTarget(
                            txMethod.getFullyQualifiedName(),
                             target.getFullyQualifiedName()
                    );
                    String chainDisplay = chains.isEmpty() ? "Direct call" : CallGraph.formatCallChain(chains.get(0));

                    violations.add(Violation.builder()
                            .ruleId(rule.getId())
                            .severity(rule.getSeverity())
                            .message(String.format("@Transactional method '%s' invokes @Async method '%s'. Context loss risk.", 
                                    txMethod.getName(), target.getName()))
                            .location(txMethod.getLocation())
                            .context(Map.of(
                                    "source", txMethod.getFullyQualifiedName(),
                                    "target", target.getFullyQualifiedName(),
                                    "callChain", chainDisplay,
                                    "remediation", rule.getRemediation() != null ? rule.getRemediation().getSummary() : "Use event listener or explicit context propagation."
                            ))
                            .fingerprint(Violation.generateFingerprint(ID, txMethod.getLocation(), Map.of("target", target.getFullyQualifiedName())))
                            .build());
                }
            }
        }
        
        return violations;
    }
}
