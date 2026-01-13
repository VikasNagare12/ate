package com.vidnyan.ate.agentic.agents;

import com.vidnyan.ate.agentic.core.Agent;
import com.vidnyan.ate.domain.graph.CallGraph;
import com.vidnyan.ate.domain.graph.DependencyGraph;
import com.vidnyan.ate.domain.model.SourceModel;
import com.vidnyan.ate.domain.rule.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Agent responsible for evaluating rules against the code structure.
 * "The Judge" - Detects violations by comparing rules to the knowledge graph.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EvaluationAgent implements Agent<EvaluationAgent.Input, EvaluationAgent.Output> {

    private final List<RuleEvaluator> ruleEvaluators;

    @Override
    public String getName() {
        return "EvaluationAgent";
    }

    @Override
    public Output execute(Input input) {
        log.info("[{}] Evaluating {} rules against code structure...",
                getName(), input.rules().size());

        List<Violation> allViolations = new ArrayList<>();

        // Build dependency graph for context
        DependencyGraph depGraph = DependencyGraph.build(input.sourceModel());

        for (RuleDefinition rule : input.rules()) {
            log.debug("[{}] Evaluating rule: {}", getName(), rule.id());

            // Find matching evaluator
            RuleEvaluator evaluator = findEvaluator(rule);
            if (evaluator == null) {
                log.warn("[{}] No evaluator for rule: {}", getName(), rule.id());
                continue;
            }

            EvaluationContext context = EvaluationContext.of(
                    rule, input.sourceModel(), input.callGraph(), depGraph);

            try {
                EvaluationResult result = evaluator.evaluate(context);
                allViolations.addAll(result.violations());

                if (result.hasViolations()) {
                    log.info("[{}] Rule {} found {} violations.",
                            getName(), rule.id(), result.violationCount());
                }
            } catch (Exception e) {
                log.error("[{}] Error evaluating rule {}: {}", getName(), rule.id(), e.getMessage());
            }
        }

        log.info("[{}] Evaluation complete. Total violations: {}", getName(), allViolations.size());

        return new Output(allViolations);
    }

    private RuleEvaluator findEvaluator(RuleDefinition rule) {
        return ruleEvaluators.stream()
                .filter(e -> e.supports(rule))
                .findFirst()
                .orElse(null);
    }

    public record Input(
            SourceModel sourceModel,
            CallGraph callGraph,
            List<RuleDefinition> rules) {
    }

    public record Output(List<Violation> violations) {
    }
}
