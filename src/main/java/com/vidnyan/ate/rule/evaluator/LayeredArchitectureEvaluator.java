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
 * Enforces Layered Architecture rules (LAYERED-ARCHITECTURE-001).
 * <p>
 * Ensures that lower layers (e.g., Service) do not depend on upper layers (e.g., Controller/Web).
 * This preserves the separation of concerns and prevents tight coupling.
 * </p>
 */
@Slf4j
@Component
public class LayeredArchitectureEvaluator implements RuleEvaluator {

    private static final String ID = "LAYERED-ARCHITECTURE-001";
    // Defaults if not specified in rule (though rule should specify)
    private static final String SERVICE_PATTERN = ".*Service.*";
    private static final String CONTROLLER_PATTERN = ".*Controller.*";

    @Override
    public boolean supports(RuleDefinition rule) {
        return ID.equals(rule.getId());
    }

    @Override
    public List<Violation> evaluate(RuleDefinition rule, SourceModel sourceModel, CallGraph callGraph, DependencyGraph dependencyGraph) {
        List<Violation> violations = new ArrayList<>();
        
        // Extract patterns from rule definition or fall back to defaults
        String sourcePatternStr = rule.getTarget() != null && rule.getTarget().getNamePattern() != null 
                ? rule.getTarget().getNamePattern() : SERVICE_PATTERN;
        
        List<String> forbiddenPackagePatterns = rule.getConstraints() != null 
                && rule.getConstraints().getMustNotDependOnPackages() != null
                ? rule.getConstraints().getMustNotDependOnPackages()
                : List.of(CONTROLLER_PATTERN); // Fallback logic or assume misconfig?

        Pattern sourcePattern = Pattern.compile(sourcePatternStr);
        // We'll treat the forbidden constraint as a regex for class/package names
        List<Pattern> forbiddenPatterns = forbiddenPackagePatterns.stream()
                .map(Pattern::compile)
                .toList();

        log.debug("Checking Layered Architecture: {} must not reach {}", sourcePattern, forbiddenPatterns);

        // 1. Find Source Methods (Services)
        // Note: Ideally we check TYPES, but CallGraph is method-based. We check if ANY method in Service calls Controller.
        List<Method> sourceMethods = new ArrayList<>();
        sourceModel.getTypes().values().stream()
                .flatMap(t -> t.getMethods().stream())
                .filter(m -> sourcePattern.matcher(m.getFullyQualifiedName()).matches())
                .forEach(sourceMethods::add);

        // 2. Check each source method for forbidden calls
        for (Method method : sourceMethods) {
                Set<String> reachableMethods = callGraph.findReachableMethods(method.getFullyQualifiedName());

            for (String reached : reachableMethods) {
                // Check if reached method matches any forbidden pattern (e.g. is in a Controller)
                boolean isForbidden = forbiddenPatterns.stream().anyMatch(p -> p.matcher(reached).matches());
                
                if (isForbidden) {
                    violations.add(Violation.builder()
                            .ruleId(rule.getId())
                            .severity(rule.getSeverity())
                            .message(String.format("Layer violation: Service method '%s' calls Presentation layer '%s'", 
                                    method.getName(), reached))
                            .location(method.getLocation())
                             .context(Map.of(
                                    "source", method.getFullyQualifiedName(),
                                    "target", reached,
                                    "remediation", rule.getRemediation().getSummary()
                            ))
                            .fingerprint(Violation.generateFingerprint(ID, method.getLocation(), Map.of("target", reached)))
                            .build());
                }
            }
        }
        
        return violations;
    }
}
