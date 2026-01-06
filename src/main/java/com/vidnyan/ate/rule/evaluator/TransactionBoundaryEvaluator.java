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

    private static final String TRANSACTIONAL = "Transactional";
    private static final String FEIGN_CLIENT = "FeignClient";

    // Explicit list of forbidden remote client types (FQNs)
    private static final List<String> FORBIDDEN_TYPES = List.of(
                    "org.springframework.web.client.RestTemplate",
                    "org.springframework.web.reactive.function.client.WebClient",
                    "java.net.http.HttpClient",
                    "org.apache.http.client.HttpClient",
                    "java.net.HttpURLConnection",
                    "com.sun.jersey.api.client.Client");

    @Override
    public boolean isApplicable(RuleDefinition rule) {
        return ID.equals(rule.getId());
    }

    @Override
    public List<Violation> detectViolations(RuleDefinition rule, SourceModel sourceModel, CallGraph callGraph,
            DependencyGraph dependencyGraph) {
        List<Violation> violations = new ArrayList<>();

        List<Method> txMethods = sourceModel.getMethodsAnnotatedWith(TRANSACTIONAL);

        // This rule targets "remote calls".
        // In the legacy JSON, target was defined by annotation "FeignClient", etc.
        // We will stick to checking constraints: mustNotInvokeAnnotatedMethods AND
        // mustNotInvokeMethodsMatching (if pattern based)
        // But for FeignClient, the interface method is annotated with
        // GetMapping/PostMapping usually, but the INTERFACE is @FeignClient.



        for (Method txMethod : txMethods) {
                Set<String> reachable = callGraph.findReachableMethods(txMethod.getFullyQualifiedName());

            for (String reachedName : reachable) {
                Method reached = sourceModel.getMethod(reachedName);
                if (reached != null) {

                        String typeFqn = reached.getContainingTypeFqn();

                        // 1. Check for specific Forbidden Types (Class/Interface matches)
                        // We check if the method belongs to one of the known HTTP client
                        // classes/interfaces.
                        boolean isRemoteType = FORBIDDEN_TYPES.contains(typeFqn);

                        // 2. Check for @FeignClient on the containing type (Interface)
                        boolean isFeign = false;
                        // Only check if not already found to avoid extra lookups
                        if (!isRemoteType) {
                                // Check if the type itself has @FeignClient annotation
                                // We need to look up the Type object from SourceModel
                                var type = sourceModel.getType(typeFqn);
                                if (type != null && type.hasAnnotation(FEIGN_CLIENT)) {
                                        isFeign = true;
                                }
                        }

                    if (isRemoteType || isFeign) {
                        List<List<String>> chains = callGraph.findCallChainsToTarget(
                                txMethod.getFullyQualifiedName(),
                                        reachedName);
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
                                                        "targetType", typeFqn,
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
