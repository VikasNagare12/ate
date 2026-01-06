package com.vidnyan.ate.domain.rule;

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
    EvaluationResult evaluate(EvaluationContext context);
    
    /**
     * Get the evaluator name for logging.
     */
    default String getName() {
        return getClass().getSimpleName();
    }
}
