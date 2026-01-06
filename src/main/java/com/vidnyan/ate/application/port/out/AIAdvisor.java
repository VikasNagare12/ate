package com.vidnyan.ate.application.port.out;

import com.vidnyan.ate.domain.rule.Violation;

import java.util.List;

/**
 * Port for AI-powered advice on violations.
 * Implemented by LLM adapters.
 */
public interface AIAdvisor {
    
    /**
     * Get AI-powered advice for violations.
     */
    AdviceResult getAdvice(List<Violation> violations);
    
    /**
     * Advice result from AI.
     */
    record AdviceResult(
        String summary,
        List<Recommendation> recommendations,
        String disclaimer
    ) {}
    
    /**
     * Single recommendation.
     */
    record Recommendation(
        String violationId,
        Priority priority,
        String explanation,
        String suggestedFix,
        List<String> references
    ) {}
    
    /**
     * Priority level.
     */
    enum Priority {
        CRITICAL,
        HIGH,
        MEDIUM,
        LOW
    }
}
