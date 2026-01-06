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
 * Enforces rule: Retryable methods must be idempotent.
 * (RETRY_WITHOUT_IDEMPOTENCY)
 * <p>
 * Checks if methods annotated with {@code @Retryable} invoke potentially non-idempotent
 * operations (like sending emails or charging payments) defined by regex patterns.
 * </p>
 */

//TODO change needed
@Slf4j
@Component
public class IdempotencyEvaluator implements RuleEvaluator {

    private static final String ID = "RETRY-IDEMPOTENCY-001";
    private static final String ID_LEGACY = "RETRY_WITHOUT_IDEMPOTENCY";
    
    private static final String RETRYABLE = "Retryable";
    
    // Default patterns if not provided in rule
    private static final List<String> DEFAULT_NON_IDEMPOTENT_PATTERNS = List.of(
            ".*EmailService.*send.*",
            ".*PaymentService.*charge.*",
            ".*NotificationService.*notify.*",
            ".*MessageProducer.*send.*"
    );

    @Override
    public boolean isApplicable(RuleDefinition rule) {
        return ID.equals(rule.getId()) || ID_LEGACY.equals(rule.getId());
    }

    @Override
    public List<Violation> detectViolations(RuleDefinition rule, SourceModel sourceModel, CallGraph callGraph, DependencyGraph dependencyGraph) {
        List<Violation> violations = new ArrayList<>();
        
        List<Method> retryableMethods = sourceModel.getMethodsAnnotatedWith(RETRYABLE);
        
        List<Pattern> compiledPatterns = DEFAULT_NON_IDEMPOTENT_PATTERNS.stream()
                .map(Pattern::compile)
                .toList();

        for (Method method : retryableMethods) {
            Set<String> reachableMethods = callGraph.findReachableMethods(method.getFullyQualifiedName());
            
            for (String reached : reachableMethods) {
                boolean isNonIdempotent = compiledPatterns.stream().anyMatch(p -> p.matcher(reached).matches());
                
                if (isNonIdempotent) {
                    List<List<String>> chains = callGraph.findCallChainsToTarget(
                            method.getFullyQualifiedName(),
                            reached
                    );
                    String chainDisplay = chains.isEmpty() ? "Direct call" : CallGraph.formatCallChain(chains.get(0));

                    violations.add(Violation.builder()
                            .ruleId(rule.getId())
                            .severity(rule.getSeverity())
                            .message(String.format("@Retryable method '%s' invokes non-idempotent operation '%s'. Risk of duplicate execution.", 
                                    method.getName(), reached))
                            .location(method.getLocation())
                             .context(Map.of(
                                    "source", method.getFullyQualifiedName(),
                                    "target", reached,
                                    "callChain", chainDisplay,
                                    "remediation", rule.getRemediation() != null ? rule.getRemediation().getSummary() : "Ensure idempotency."
                            ))
                            .fingerprint(Violation.generateFingerprint(ID, method.getLocation(), Map.of("target", reached)))
                            .build());
                }
            }
        }
        
        return violations;
    }
}
