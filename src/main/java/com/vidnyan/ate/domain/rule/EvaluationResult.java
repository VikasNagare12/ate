package com.vidnyan.ate.domain.rule;

import java.time.Duration;
import java.util.List;

/**
 * Result of evaluating a rule against the codebase.
 */
public record EvaluationResult(
    String ruleId,
    List<Violation> violations,
    Duration executionTime,
    int nodesAnalyzed,
    EvaluationStatus status,
    String errorMessage
) {
    
    public enum EvaluationStatus {
        SUCCESS,
        ERROR,
        SKIPPED
    }
    
    /**
     * Create a successful result.
     */
    public static EvaluationResult success(String ruleId, List<Violation> violations, 
                                            Duration duration, int nodes) {
        return new EvaluationResult(ruleId, violations, duration, nodes, 
                EvaluationStatus.SUCCESS, null);
    }
    
    /**
     * Create an error result.
     */
    public static EvaluationResult error(String ruleId, String message) {
        return new EvaluationResult(ruleId, List.of(), Duration.ZERO, 0, 
                EvaluationStatus.ERROR, message);
    }
    
    /**
     * Create a skipped result.
     */
    public static EvaluationResult skipped(String ruleId, String reason) {
        return new EvaluationResult(ruleId, List.of(), Duration.ZERO, 0, 
                EvaluationStatus.SKIPPED, reason);
    }
    
    /**
     * Check if any violations were found.
     */
    public boolean hasViolations() {
        return !violations.isEmpty();
    }
    
    /**
     * Get violation count.
     */
    public int violationCount() {
        return violations.size();
    }
}
