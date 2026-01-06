package com.vidnyan.ate.adapter.out.evaluator;

import com.vidnyan.ate.domain.graph.DependencyGraph;
import com.vidnyan.ate.domain.model.Location;
import com.vidnyan.ate.domain.rule.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Detects circular dependencies between packages.
 * Circular dependencies make code harder to maintain and test.
 */
@Slf4j
@Component
@Order(20)
public class CircularDependencyEvaluatorV2 implements RuleEvaluator {
    
    @Override
    public boolean supports(RuleDefinition rule) {
        return "CIRCULAR-DEP-001".equals(rule.id()) 
                || RuleDefinition.Category.CIRCULAR_DEPENDENCY == rule.category();
    }
    
    @Override
    public EvaluationResult evaluate(EvaluationContext context) {
        Instant start = Instant.now();
        DependencyGraph depGraph = context.dependencyGraph();
        RuleDefinition rule = context.rule();
        
        log.debug("Evaluating circular dependencies");
        
        List<Violation> violations = new ArrayList<>();
        
        // Find all cycles
        List<List<String>> cycles = depGraph.findCircularDependencies();
        log.debug("Found {} circular dependency cycles", cycles.size());
        
        for (List<String> cycle : cycles) {
            String cycleStr = formatCycle(cycle);
            
            violations.add(Violation.builder()
                    .ruleId(rule.id())
                    .ruleName(rule.name())
                    .severity(rule.severity())
                    .message(String.format(
                            "Circular dependency detected: %s. " +
                            "This makes the code harder to maintain and test.",
                            cycleStr
                    ))
                    .location(Location.at("package-level", 0, 0))
                    .callChain(cycle)
                    .context(Map.of("cycleLength", cycle.size()))
                    .build());
        }
        
        Duration duration = Duration.between(start, Instant.now());
        return EvaluationResult.success(rule.id(), violations, duration, depGraph.getAllPackages().size());
    }
    
    private String formatCycle(List<String> cycle) {
        if (cycle.isEmpty()) return "";
        
        // Simplify package names for readability
        List<String> simplified = cycle.stream()
                .map(this::simplifyPackage)
                .toList();
        
        return String.join(" → ", simplified);
    }
    
    private String simplifyPackage(String pkg) {
        // Keep last 2-3 parts of package name
        String[] parts = pkg.split("\\.");
        if (parts.length <= 3) return pkg;
        return parts[parts.length - 3] + "." + parts[parts.length - 2] + "." + parts[parts.length - 1];
    }
}
