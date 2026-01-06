package com.vidnyan.ate.adapter.out.ai;

import com.vidnyan.ate.application.port.out.AIAdvisor;
import com.vidnyan.ate.domain.rule.RuleDefinition;
import com.vidnyan.ate.domain.rule.Violation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Mock AI Advisor for development/testing.
 * Can be replaced with real LLM integration (OpenAI, Anthropic, etc.)
 */
@Slf4j
@Component
public class MockAIAdvisor implements AIAdvisor {
    
    @Override
    public AdviceResult getAdvice(List<Violation> violations) {
        log.info("AI Advisor analyzing {} violations...", violations.size());
        
        if (violations.isEmpty()) {
            return new AdviceResult(
                    "Great job! No architectural violations found. Your codebase is clean.",
                    List.of(new Recommendation(
                            null,
                            Priority.LOW,
                            "Maintain Cleanliness",
                            "Continue running ATE in your CI/CD pipeline to prevent regression.",
                            List.of()
                    )),
                    "This is mock advice for development purposes."
            );
        }
        
        List<Recommendation> recommendations = violations.stream()
                .limit(5) // Top 5 issues
                .map(this::createRecommendation)
                .collect(Collectors.toList());
        
        String summary = String.format(
                "Found %d architectural violations. %d are critical and should be addressed immediately. " +
                "Focus on transaction safety issues first as they can cause data inconsistencies.",
                violations.size(),
                violations.stream().filter(v -> v.severity() == RuleDefinition.Severity.BLOCKER).count()
        );
        
        return new AdviceResult(summary, recommendations, 
                "This is mock advice. Integrate with OpenAI or Anthropic for real recommendations.");
    }
    
    private Recommendation createRecommendation(Violation violation) {
        Priority priority = mapPriority(violation.severity());
        
        String explanation = switch (violation.ruleId()) {
            case "TX-BOUNDARY-001" -> 
                    "Remote calls inside @Transactional methods can cause issues if the remote " +
                    "service is slow or unavailable, holding database connections.";
            case "ASYNC-TX-BOUNDARY-001" -> 
                    "@Async methods run in a different thread, so @Transactional context is lost. " +
                    "The transaction won't propagate to the async method.";
            case "JDBC-RETRY-SAFETY-001" -> 
                    "Database operations without @Retry can fail due to transient errors. " +
                    "Add retry logic to improve resilience.";
            case "CIRCULAR-DEPENDENCY-001" -> 
                    "Circular package dependencies make the code harder to maintain and test. " +
                    "Consider introducing an abstraction layer.";
            default -> "Review this violation and apply the suggested fix.";
        };
        
        String suggestedFix = switch (violation.ruleId()) {
            case "TX-BOUNDARY-001" -> 
                    "Move the remote call outside the @Transactional method, or use a separate service.";
            case "ASYNC-TX-BOUNDARY-001" -> 
                    "If you need transactions in async methods, start a new transaction in the async method itself.";
            case "JDBC-RETRY-SAFETY-001" -> 
                    "Add @Retry annotation with appropriate maxAttempts and backoff configuration.";
            case "CIRCULAR-DEPENDENCY-001" -> 
                    "Extract shared code into a common module or use dependency inversion.";
            default -> violation.message();
        };
        
        return new Recommendation(
                violation.ruleId(),
                priority,
                explanation,
                suggestedFix,
                List.of("https://docs.spring.io/spring-framework/reference/")
        );
    }
    
    private Priority mapPriority(RuleDefinition.Severity severity) {
        return switch (severity) {
            case BLOCKER -> Priority.CRITICAL;
            case ERROR -> Priority.HIGH;
            case WARN -> Priority.MEDIUM;
            case INFO -> Priority.LOW;
        };
    }
}
