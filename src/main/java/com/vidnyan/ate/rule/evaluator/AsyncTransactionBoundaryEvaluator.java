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
 * Enforces the Async Transaction Boundary rule (ASYNC-TX-BOUNDARY-001).
 * <p>
 * This rule ensures that methods annotated with {@code @Async} do not invoke
 * methods annotated with {@code @Transactional}. Propagating transactions into 
 * async threads is unsafe and can lead to data inconsistency or lazy loading errors.
 * </p>
 */
@Slf4j
@Component
public class AsyncTransactionBoundaryEvaluator implements RuleEvaluator {

    private static final String ID = "ASYNC-TX-BOUNDARY-001";
    private static final String ASYNC_ANNOTATION = "Async";
    private static final String TRANSACTIONAL_ANNOTATION = "Transactional";


    @Override
    public boolean supports(RuleDefinition rule) {
        return ID.equals(rule.getId());
    }

    @Override
    public List<Violation> evaluate(RuleDefinition rule, SourceModel sourceModel, CallGraph callGraph, DependencyGraph dependencyGraph) {
        List<Violation> violations = new ArrayList<>();
        
        // 1. Identify Target Audience: Methods with @Async
        List<Method> asyncMethods = sourceModel.getMethodsAnnotatedWith(ASYNC_ANNOTATION);
        log.debug("Evaluating {} rule on {} @Async methods", ID, asyncMethods.size());



        for (Method asyncMethod : asyncMethods) {
            // 2. Traverse: Look for downstream calls
            Set<String> reachableMethods = callGraph.findReachableMethods(
                    asyncMethod.getFullyQualifiedName()
            );

            // 3. Inspect: Check if any reachable method is @Transactional
            for (String reachableMethodName : reachableMethods) {
                Method reachedMethod = sourceModel.getMethod(reachableMethodName);
                if (reachedMethod != null && reachedMethod.hasAnnotation(TRANSACTIONAL_ANNOTATION)) {
                    
                    // 4. Report Violation
                    List<List<String>> chains = callGraph.findCallChainsToTarget(
                            asyncMethod.getFullyQualifiedName(),
                            reachedMethod.getFullyQualifiedName()
                    );

                    String chainDisplay = chains.isEmpty() ? "Direct call" : CallGraph.formatCallChain(chains.get(0));

                    violations.add(Violation.builder()
                            .ruleId(rule.getId())
                            .severity(rule.getSeverity())
                            .message(String.format("@Async method '%s' invokes @Transactional method '%s'. Transaction context is NOT propagated.",
                                    asyncMethod.getName(), reachedMethod.getName()))
                            .location(asyncMethod.getLocation())
                            .context(Map.of(
                                    "sourceMethod", asyncMethod.getFullyQualifiedName(),
                                    "targetMethod", reachedMethod.getFullyQualifiedName(),
                                    "callChain", chainDisplay,
                                    "remediation", rule.getRemediation() != null ? rule.getRemediation().getSummary() : "Please fix this."
                            ))
                            .fingerprint(Violation.generateFingerprint(
                                    ID, 
                                    asyncMethod.getLocation(), 
                                    Map.of("target", reachedMethod.getFullyQualifiedName())
                            ))
                            .build());
                }
            }
        }

        return violations;
    }
}
