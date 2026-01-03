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
import java.util.regex.Pattern;

/**
 * Enforces rule: Remote calls inside @Transactional methods.
 * (TX-BOUNDARY-001)
 * <p>
 * Database transactions should be short-lived. Making remote calls (Feigh,
 * RestTemplate)
 * inside a transaction extends the transaction duration, holding database
 * connections hostage
 * and potentially causing pool exhaustion or lock contention.
 * </p>
 */
@Slf4j
@Component
public class TransactionBoundaryEvaluator implements RuleEvaluator {

    private static final String ID = "TX-BOUNDARY-001";
    private static final String ID_LEGACY = "TRANSACTION_BOUNDARY_VIOLATION";

    private static final String TRANSACTIONAL = "Transactional";

    // Default remote clients to check for
    private static final List<String> DEFAULT_REMOTE_ANNOTATIONS = List.of(
            "FeignClient",
            "RestTemplate", // likely field checking, but let's assume methods annotated or classes
            "WebClient");

    @Override
    public boolean supports(RuleDefinition rule) {
        return ID.equals(rule.getId()) || ID_LEGACY.equals(rule.getId());
    }

    @Override
    public List<Violation> evaluate(RuleDefinition rule, SourceModel sourceModel, CallGraph callGraph,
            DependencyGraph dependencyGraph) {
        List<Violation> violations = new ArrayList<>();

        List<Method> txMethods = sourceModel.getMethodsAnnotatedWith(TRANSACTIONAL);

        // This rule targets "remote calls".
        // In the legacy JSON, target was defined by annotation "FeignClient", etc.
        // We will stick to checking constraints: mustNotInvokeAnnotatedMethods AND
        // mustNotInvokeMethodsMatching (if pattern based)
        // But for FeignClient, the interface method is annotated with
        // GetMapping/PostMapping usually, but the INTERFACE is @FeignClient.

        List<String> forbiddenAnnotations = rule.getConstraints() != null
                && rule.getConstraints().getMustNotInvokeAnnotatedMethods() != null
                        ? rule.getConstraints().getMustNotInvokeAnnotatedMethods()
                        : DEFAULT_REMOTE_ANNOTATIONS; // "FeignClient" logic needs special handling for TYPE vs METHOD
                                                      // annotation

        int maxDepth = rule.getConstraints() != null && rule.getConstraints().getMaxCallDepth() != null
                ? rule.getConstraints().getMaxCallDepth()
                : 5;

        for (Method txMethod : txMethods) {
            Set<String> reachable = callGraph.findReachableMethods(txMethod.getFullyQualifiedName(), maxDepth);

            for (String reachedName : reachable) {
                Method reached = sourceModel.getMethod(reachedName);
                if (reached != null) {
                    // Check if method OR ITS CLASS has the forbidden annotation
                    // FeignClient is on the class (interface)
                    boolean isRemote = forbiddenAnnotations.stream().anyMatch(ann -> reached.hasAnnotation(ann)
                            || sourceModel.getType(reached.getContainingTypeFqn()).hasAnnotation(ann));

                    if (isRemote) {
                        List<List<String>> chains = callGraph.findCallChainsToTarget(
                                txMethod.getFullyQualifiedName(),
                                reachedName,
                                maxDepth);
                        String chainDisplay = chains.isEmpty() ? "Direct call"
                                : CallGraph.formatCallChain(chains.get(0));

                        violations.add(Violation.builder()
                                .ruleId(rule.getId())
                                .severity(rule.getSeverity())
                                .message(String.format(
                                        "@Transactional method '%s' performs remote call via '%s'. Risk of long transaction.",
                                        txMethod.getName(), reached.getName()))
                                .location(txMethod.getLocation())
                                .context(Map.of(
                                        "source", txMethod.getFullyQualifiedName(),
                                        "target", reachedName,
                                        "callChain", chainDisplay,
                                        "remediation",
                                        rule.getRemediation() != null ? rule.getRemediation().getSummary()
                                                : "Move remote call outside transaction."))
                                .fingerprint(Violation.generateFingerprint(ID, txMethod.getLocation(),
                                        Map.of("target", reachedName)))
                                .build());
                    }
                }
            }
        }

        return violations;
    }
}
