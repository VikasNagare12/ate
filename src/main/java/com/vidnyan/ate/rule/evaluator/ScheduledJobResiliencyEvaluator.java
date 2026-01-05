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
 * Enforces rule: Scheduled jobs must use retry/resilience logic.
 * (SCHEDULED-RESILIENCY-001)
 * <p>
 * Since scheduled jobs run in the background, failures are often silent or hard to recover manually.
 * They should employ self-healing mechanisms like Retry or Circuit breaking.
 * </p>
 */
@Slf4j
@Component
public class ScheduledJobResiliencyEvaluator implements RuleEvaluator {

    private static final String ID = "SCHEDULED-RESILIENCY-001";
    private static final String ID_LEGACY = "SCHEDULED_JOB_RESILIENCY";
    
    private static final String SCHEDULED = "Scheduled";
    
    // Default resilience annotations if not provided
    private static final List<String> DEFAULT_RESILIENCE_ANNOTATIONS = List.of(
            "Retryable",
            "CircuitBreaker",
            "Resilience4j" // simplified
    );

    @Override
    public boolean supports(RuleDefinition rule) {
        return ID.equals(rule.getId()) || ID_LEGACY.equals(rule.getId());
    }

    @Override
    public List<Violation> evaluate(RuleDefinition rule, SourceModel sourceModel, CallGraph callGraph, DependencyGraph dependencyGraph) {
        List<Violation> violations = new ArrayList<>();
        
        List<Method> scheduledMethods = sourceModel.getMethodsAnnotatedWith(SCHEDULED);
        
        List<String> resilienceAnnotations = DEFAULT_RESILIENCE_ANNOTATIONS;


        for (Method method : scheduledMethods) {
            // Check if method itself has resilience (depth 0 optimization)
            boolean selfResilient = resilienceAnnotations.stream().anyMatch(method::hasAnnotation);
            if (selfResilient) continue;

            Set<String> reachable = callGraph.findReachableMethods(method.getFullyQualifiedName());
            
            // Check if ANY reachable method has ANY resilience annotation
            boolean resilientCallFound = false;
            for (String reachedName : reachable) {
                Method reached = sourceModel.getMethod(reachedName);
                if (reached != null) {
                    if (resilienceAnnotations.stream().anyMatch(reached::hasAnnotation)) {
                        resilientCallFound = true;
                        break;
                    }
                }
            }
            
            if (!resilientCallFound) {
                 violations.add(Violation.builder()
                        .ruleId(rule.getId())
                        .severity(rule.getSeverity())
                        .message(String.format("@Scheduled method '%s' has no resilience mechanism. Add @Retryable or similar.", 
                                method.getName()))
                        .location(method.getLocation())
                         .context(Map.of(
                                "source", method.getFullyQualifiedName(),
                                "required", resilienceAnnotations,
                                "remediation", rule.getRemediation() != null ? rule.getRemediation().getSummary() : "Add retry logic."
                        ))
                        .fingerprint(Violation.generateFingerprint(ID, method.getLocation(), Map.of()))
                        .build());
            }
        }
        
        return violations;
    }
}
