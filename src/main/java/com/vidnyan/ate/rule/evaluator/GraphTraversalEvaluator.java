package com.vidnyan.ate.rule.evaluator;

import com.vidnyan.ate.graph.CallGraph;
import com.vidnyan.ate.graph.DependencyGraph;
import com.vidnyan.ate.model.SourceModel;
import com.vidnyan.ate.rule.RuleDefinition;
import com.vidnyan.ate.rule.Violation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

/**
 * Evaluates rules using generic graph traversal.
 * 
 * @deprecated As of ATE 2.0, this generic evaluator is replaced by specific,
 *             domain-centric
 *             evaluator implementations (e.g.,
 *             {@link AsyncTransactionBoundaryEvaluator}).
 *             This class is retained for backward compatibility but is no
 *             longer maintained.
 *             It does not support the new {@link RuleDefinition} schema.
 */
@Deprecated
@Slf4j
@Component
public class GraphTraversalEvaluator implements RuleEvaluator {

    @Override
    public boolean supports(RuleDefinition rule) {
        // This legacy evaluator does not support the new v2 schema rules.
        // It only supported v1 rules which had a "query" field, now removed.
        return false;
    }

    @Override
    public List<Violation> evaluate(RuleDefinition rule, SourceModel sourceModel, CallGraph callGraph, DependencyGraph dependencyGraph) {
        log.warn(
                "Invoked deprecated GraphTraversalEvaluator for rule: {}. This evaluator is non-functional with the new rule schema.",
                rule.getId());
        return Collections.emptyList();
    }
}
