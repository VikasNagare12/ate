package com.vidnyan.ate.domain.rule;

import com.vidnyan.ate.analyzer.SootUpEvaluationContext;

/**
 * Interface for rule evaluators.
 * Each evaluator handles specific rule types.
 */
public interface RuleEvaluator {
    
    /**
     * Check if this evaluator can handle the given rule.
     */
    boolean supports(RuleDefinition rule);
    
    /**
     * Evaluate the rule against the codebase.
     */
    EvaluationResult evaluate(SootUpEvaluationContext context);
    
    /**
     * Get the evaluator name for logging.
     */
    default String getName() {
        return getClass().getSimpleName();
    }
}
