package com.vidnyan.ate.rule.evaluator;

import com.vidnyan.ate.graph.CallGraph;
import com.vidnyan.ate.graph.DependencyGraph;
import com.vidnyan.ate.model.Location;
import com.vidnyan.ate.model.SourceModel;
import com.vidnyan.ate.rule.RuleDefinition;
import com.vidnyan.ate.rule.Violation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Detects circular dependencies between components or packages.
 * (CIRCULAR-DEPENDENCY-001)
 */
@Slf4j
@Component
public class CircularDependencyEvaluator implements RuleEvaluator {

    private static final String ID = "CIRCULAR-DEPENDENCY-001";

    @Override
    public boolean isApplicable(RuleDefinition rule) {
        return ID.equals(rule.getId());
    }

    @Override
    public List<Violation> detectViolations(RuleDefinition rule, SourceModel sourceModel, CallGraph callGraph, DependencyGraph dependencyGraph) {
        List<Violation> violations = new ArrayList<>();
        
        if (dependencyGraph.hasCircularDependencies()) {
            List<List<String>> cycles = dependencyGraph.getCircularDependencies();
            for (List<String> cycle : cycles) {
                String cycleStr = String.join(" -> ", cycle);
                
                violations.add(Violation.builder()
                        .ruleId(rule.getId())
                        .severity(rule.getSeverity())
                        .message("Circular dependency detected: " + cycleStr)
                        // Architecture violations often apply to the whole system, but we can try to pinpoint one file if needed.
                        // For now, using a generic location or the first element's location if available.
                        .location(Location.builder().filePath("System").line(0).column(0).build()) 
                        .context(Map.of(
                                "cycle", cycle,
                                "remediation", rule.getRemediation() != null ? rule.getRemediation().getSummary() : "Break the cycle."
                        ))
                        .fingerprint(Violation.generateFingerprint(ID, Location.builder().filePath("root").build(), Map.of("cycle", cycleStr)))
                        .build());
            }
        }
        
        return violations;
    }
}
