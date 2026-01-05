package com.vidnyan.ate.advisor;

import com.vidnyan.ate.report.ReportModel;
import com.vidnyan.ate.rule.Violation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * A simple mock advisor that generates deterministic advice based on violations.
 * This satisfies the initial requirement without external AI calls (YAGNI).
 */
@Slf4j
@Component
public class MockAdvisor implements Advisor {

    @Override
    public Advice analyze(ReportModel report) {
        log.info("AI Advisor analyzing {} violations...", report.getSummary().getTotalViolations());
        
        List<Advice.Suggestion> suggestions = new ArrayList<>();
        
        if (report.getSummary().getTotalViolations() == 0) {
            return Advice.builder()
                    .summary("Great job! No architectural violations found. Your codebase is clean.")
                    .suggestions(List.of(
                            Advice.Suggestion.builder()
                                    .title("Maintain Cleanliness")
                                    .description("Continue running ATE in your CI/CD pipeline to prevent regression.")
                                    .impact("High")
                                    .build()
                    ))
                    .build();
        }
        
        // Group violations by rule
        Map<String, List<Violation>> byRule = report.getViolations().stream()
                .collect(Collectors.groupingBy(Violation::getRuleId));
        
        byRule.forEach((ruleId, violations) -> {
            if ("LAYERED_ARCHITECTURE_VIOLATION".equals(ruleId)) {
                suggestions.add(Advice.Suggestion.builder()
                        .title("Enforce Layer Boundaries")
                        .description("Found Services calling Controllers. Consider using an event bus or restructuring your packages to invert dependencies.")
                        .impact("High")
                        .build());
            } else if ("SCHEDULED_JOB_RESILIENCY".equals(ruleId)) {
                suggestions.add(Advice.Suggestion.builder()
                        .title("Improve Job Reliability")
                        .description("Scheduled jobs identified without resilience patterns. Apply @Retryable or wrap logic in a resilience service.")
                        .impact("Medium")
                        .build());
            }
        });
        
        return Advice.builder()
                .summary("Codebase analysis complete. Found potential architectural improvements.")
                .suggestions(suggestions)
                .build();
    }
}
