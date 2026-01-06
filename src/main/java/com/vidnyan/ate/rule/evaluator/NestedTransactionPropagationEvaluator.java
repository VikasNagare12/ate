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
 * Enforces rule: Nested Transaction Propagation.
 * (NESTED-TX-PROPAGATION-001)
 * <p>
 * Detects when a @Transactional method calls another @Transactional method.
 * Default propagation (REQUIRED) merges them into one transaction, which might
 * not be intended.
 * </p>
 */
@Slf4j
@Component
public class NestedTransactionPropagationEvaluator implements RuleEvaluator {

    private static final String ID = "NESTED-TX-PROPAGATION-001";

    private static final String TRANSACTIONAL = "Transactional";

    @Override
    public boolean isApplicable(RuleDefinition rule) {
        return ID.equals(rule.getId());
    }

    @Override
    public List<Violation> detectViolations(RuleDefinition rule, SourceModel sourceModel, CallGraph callGraph,
            DependencyGraph dependencyGraph) {
        List<Violation> violations = new ArrayList<>();

        List<Method> txMethods = sourceModel.getMethodsAnnotatedWith(TRANSACTIONAL);

        for (Method txMethod : txMethods) {
            Set<String> reachable = callGraph.findReachableMethods(txMethod.getFullyQualifiedName());

            for (String targetName : reachable) {
                // Avoid self-reference if needed, though recursive calls are also nested
                // transactions.
                if (targetName.equals(txMethod.getFullyQualifiedName())) {
                    continue;
                }

                Method target = sourceModel.getMethod(targetName);
                if (target != null && target.hasAnnotation(TRANSACTIONAL)) {

                    List<List<String>> chains = callGraph.findCallChainsToTarget(
                            txMethod.getFullyQualifiedName(),
                            target.getFullyQualifiedName());
                    String chainDisplay = chains.isEmpty() ? "Direct call" : CallGraph.formatCallChain(chains.get(0));

                    violations.add(Violation.builder()
                            .ruleId(rule.getId())
                            .severity(rule.getSeverity())
                            .message(String.format(
                                    "Nested transaction detected: '%s' -> '%s'. Verify propagation behavior.",
                                    txMethod.getName(), target.getName()))
                            .location(txMethod.getLocation())
                            .context(Map.of(
                                    "source", txMethod.getFullyQualifiedName(),
                                    "target", target.getFullyQualifiedName(),
                                    "callChain", chainDisplay,
                                    "remediation",
                                    rule.getRemediation() != null ? rule.getRemediation().getSummary()
                                            : "Check propagation."))
                            .fingerprint(Violation.generateFingerprint(ID, txMethod.getLocation(),
                                    Map.of("target", target.getFullyQualifiedName())))
                            .build());
                }
            }
        }

        return violations;
    }
}
