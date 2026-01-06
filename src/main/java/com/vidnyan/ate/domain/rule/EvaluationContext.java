package com.vidnyan.ate.domain.rule;

import com.vidnyan.ate.domain.graph.CallGraph;
import com.vidnyan.ate.domain.graph.DependencyGraph;
import com.vidnyan.ate.domain.model.SourceModel;

/**
 * Context provided to rule evaluators.
 */
public record EvaluationContext(
    RuleDefinition rule,
    SourceModel sourceModel,
    CallGraph callGraph,
    DependencyGraph dependencyGraph
) {
    
    /**
     * Create context.
     */
    public static EvaluationContext of(
            RuleDefinition rule,
            SourceModel sourceModel,
            CallGraph callGraph,
            DependencyGraph dependencyGraph
    ) {
        return new EvaluationContext(rule, sourceModel, callGraph, dependencyGraph);
    }
}
