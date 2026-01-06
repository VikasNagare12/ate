package com.vidnyan.ate.rule.evaluator;

import com.vidnyan.ate.graph.CallGraph;
import com.vidnyan.ate.graph.DependencyGraph;
import com.vidnyan.ate.model.SourceModel;
import com.vidnyan.ate.rule.RuleDefinition;
import com.vidnyan.ate.rule.Violation;

import java.util.List;

/**
 * Strategy interface for evaluating different types of rules.
 */
public interface RuleEvaluator {
    
    /**
     * Check if this evaluator isApplicable the given rule.
     */
    boolean isApplicable(RuleDefinition rule);
    
    /**
     * detectViolations the rule against the model and graphs.
     */
    List<Violation> detectViolations(RuleDefinition rule,
                            SourceModel sourceModel, 
                            CallGraph callGraph, 
                            DependencyGraph dependencyGraph);
}
