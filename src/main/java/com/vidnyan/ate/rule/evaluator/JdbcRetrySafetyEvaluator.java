package com.vidnyan.ate.rule.evaluator;

import com.vidnyan.ate.graph.CallGraph;
import com.vidnyan.ate.graph.DependencyGraph;
import com.vidnyan.ate.model.Method;
import com.vidnyan.ate.model.SourceModel;
import com.vidnyan.ate.rule.RuleDefinition;
import com.vidnyan.ate.rule.Violation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;

@Slf4j
@Component
public class JdbcRetrySafetyEvaluator implements RuleEvaluator {

    private static final String JDBC_TEMPLATE = "org.springframework.jdbc.core.JdbcTemplate";
    private static final String NAMED_PARAM_JDBC = "org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate";
    private static final String RETRY_ANNOTATION = "Retry"; // Resilience4j
    private static final String ASYNC_ANNOTATION = "Async"; // Spring

    @Override
    public boolean supports(RuleDefinition rule) {
        return "JDBC-RETRY-SAFETY-001".equals(rule.getId());
    }

    @Override
    public List<Violation> evaluate(RuleDefinition rule, SourceModel sourceModel, CallGraph callGraph,
            DependencyGraph dependencyGraph) {
        List<Violation> violations = new ArrayList<>();

        // 1. Identify all methods in the codebase that call JDBC Template methods
        Set<String> jdbcCallers = findJdbcCallers(sourceModel, callGraph);

        for (String caller : jdbcCallers) {
            // 2. For each usage, traverse upstream to find entry points or max depth
            // We want to verify that AT LEAST ONE path to this usage has a Valid Retry
            // Configuration
            // AND that ALL paths to this usage are protected?
            // "Any execution path that invokes database access... must be protected" -> ALL
            // paths must be protected.

            // We perform a backward traversal (finding callers of callers).
            // This is effectively finding all "Call Chains" ending at 'caller'.

            List<List<String>> inverseChains = findUpstreamChains(caller, callGraph);

            for (List<String> chain : inverseChains) {
                // chain[0] is the usage (lowest level in our code)
                // chain[N] is the root (entry point)

                if (!isProtectedByRetry(chain, sourceModel)) {
                    violations.add(createViolation(rule, chain));
                }
            }
        }

        return violations;
    }

    private Set<String> findJdbcCallers(SourceModel sourceModel, CallGraph callGraph) {
        Set<String> callers = new HashSet<>();
        // Iterate all methods in our source model
        for (Method method : sourceModel.getMethods().values()) {
            List<String> callees = callGraph.getCallees(method.getFullyQualifiedName());
            for (String callee : callees) {
                // Check if callee belongs to JdbcTemplate classes
                if (callee.startsWith(JDBC_TEMPLATE) || callee.startsWith(NAMED_PARAM_JDBC)) {
                    callers.add(method.getFullyQualifiedName());
                    break; // Found one usage, that's enough to mark method as a sink
                }
            }
        }
        return callers;
    }

    private List<List<String>> findUpstreamChains(String startNode, CallGraph callGraph) {
        List<List<String>> result = new ArrayList<>();
        // DFS Backwards
        findUpstreamRecursive(startNode, new ArrayList<>(), new HashSet<>(), result, callGraph, 0);
        return result;
    }

    private void findUpstreamRecursive(String currentMethod, List<String> currentChain, Set<String> visited,
            List<List<String>> result, CallGraph callGraph, int depth) {
        if (depth > 100) {
            // Max depth reached, consider this a complete chain for analysis
            List<String> fullChain = new ArrayList<>(currentChain);
            fullChain.add(currentMethod);
            result.add(fullChain);
            return;
        }

        if (visited.contains(currentMethod)) {
            return; // Cycle
        }

        currentChain.add(currentMethod);
        visited.add(currentMethod);

        List<String> callers = callGraph.getCallers(currentMethod);

        if (callers.isEmpty()) {
            // Root reached
            result.add(new ArrayList<>(currentChain));
        } else {
            for (String caller : callers) {
                // Only traverse if caller is in our source model (not external)
                // CallGraph usually contains only known methods in keys, but strict check is
                // good
                findUpstreamRecursive(caller, currentChain, visited, result, callGraph, depth + 1);
            }
        }

        visited.remove(currentMethod);
        currentChain.remove(currentChain.size() - 1);
    }

    private boolean isProtectedByRetry(List<String> chain, SourceModel sourceModel) {
        // chain: [JDBC_Usage, Caller1, Caller2, Root]
        // We traverse from Root (Map Entry/Controller) down to usage?
        // No, the list is [Bottom, Middle, Top].
        // Index 0 = Bottom (JDBC Usage code). Index N = Top.
        // "Retry must exist at or above the @Async boundary"

        boolean retryFound = false;
        int retryIndex = -1;
        // Rule: "Retry below an @Async boundary is invalid"
        // Means if we find Async at index 'a', and Retry at index 'r'.
        // If 'r' < 'a' (Retry is "lower"/closer to usage than Async), it is INVALID.
        // So all Retries must be >= any Async.

        for (int i = 0; i < chain.size(); i++) {
            String methodSig = chain.get(i);
            Method method = sourceModel.getMethod(methodSig);
            if (method == null)
                continue;

            boolean hasRetry = method.hasAnnotation(RETRY_ANNOTATION);
            boolean hasAsync = method.hasAnnotation(ASYNC_ANNOTATION);

            if (hasRetry) {
                retryFound = true;
                retryIndex = i;
            }

            if (hasAsync) {
                // Track where Async is.
            }

            // Check constraint: Retry below Async
            if (hasAsync && retryFound) {
                // We have both.
                // If we found Retry at a lower index (previously), and now we found Async at
                // 'i' (higher).
                // invalid: async(higher) -> retry(lower)
                // i > retryIndex
                if (i > retryIndex) {
                    // This means Async is "above" Retry. -> INVALID.
                    // But wait, the list is [Bottom, Middle, Top].
                    // i increases as we go UP the stack.
                    // index 0 is JDBC usage (Bottom).
                    // index N is Root (Top).
                    // "Retry below Async" -> Retry is closer to Bottom.
                    // So Retry Index < Async Index -> INVALID.
                    // Here: asyncIndex = i. retryIndex (previously set) < i.
                    // YES.
                    return false;
                }
            }
        }

        // If no Async, just need Retry.
        // If Async exists, we checked the order above?
        // Wait, loop finishes. We might find Async at index 5, and Retry at index 6.
        // In that case: retry(6) > async(5). Retry is ABOVE Async. VALID.
        // What if Async at 5, Retry at 2?
        // Loop i=2: find Retry. retryIndex=2.
        // Loop i=5: find Async. asyncIndex=5.
        // Check: if (hasAsync && retryFound) -> (true && true).
        // if (i > retryIndex) -> 5 > 2 -> true.
        // Returns false. CORRECT.

        return retryFound;
    }

    private Violation createViolation(RuleDefinition rule, List<String> chain) {
        String method = chain.isEmpty() ? "Unknown" : chain.get(0);

        // Format chain for display: Top -> Bottom
        List<String> displayChain = new ArrayList<>(chain);
        Collections.reverse(displayChain);
        String chainStr = String.join(" -> ", displayChain);

        return Violation.builder()
                .ruleId(rule.getId())
                .message("JDBC access path not protected by @Retry or has invalid @Async boundary: " + chainStr)
                .location(null) // Can look up location from SourceModel if needed
                .context(Map.of("elementId", method))
                .build();
    }
}
