package com.vidnyan.ate.agentic;

import com.vidnyan.ate.agentic.agents.CodeStructureAgent;
import com.vidnyan.ate.agentic.agents.EvaluationAgent;
import com.vidnyan.ate.agentic.agents.RuleDefinitionAgent;
import com.vidnyan.ate.application.port.out.AIAdvisor;
import com.vidnyan.ate.application.port.out.SourceCodeParser;
import com.vidnyan.ate.domain.rule.Violation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.List;

/**
 * Orchestrator coordinates the agents to perform a complete analysis.
 * Acts as the "conductor" of the agent swarm.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class Orchestrator {

    private final CodeStructureAgent codeStructureAgent;
    private final RuleDefinitionAgent ruleDefinitionAgent;
    private final EvaluationAgent evaluationAgent;
    private final AIAdvisor aiAdvisor;

    /**
     * Execute a full analysis workflow.
     */
    public AnalysisResult analyze(Path sourcePath, List<String> ruleIds) {
        log.info("=== Agentic Analysis Started ===");
        log.info("Source: {}", sourcePath);

        // Phase 1: Code Structure Analysis
        log.info("Phase 1: Calling CodeStructureAgent...");
        CodeStructureAgent.Output codeOutput = codeStructureAgent.execute(
                new CodeStructureAgent.Input(sourcePath));

        // Phase 2: Rule Loading
        log.info("Phase 2: Calling RuleDefinitionAgent...");
        RuleDefinitionAgent.Output ruleOutput = ruleDefinitionAgent.execute(
                new RuleDefinitionAgent.Input(ruleIds));

        // Phase 3: Evaluation
        log.info("Phase 3: Calling EvaluationAgent...");
        EvaluationAgent.Output evalOutput = evaluationAgent.execute(
                new EvaluationAgent.Input(
                        codeOutput.sourceModel(),
                        codeOutput.callGraph(),
                        ruleOutput.rules()));

        // Phase 4 (Optional): AI Advice
        AIAdvisor.AdviceResult advice = null;
        if (!evalOutput.violations().isEmpty()) {
            log.info("Phase 4: Generating AI advice for {} violations...", evalOutput.violations().size());
            advice = aiAdvisor.getAdvice(evalOutput.violations());
        }

        log.info("=== Agentic Analysis Complete ===");
        log.info("Violations: {}", evalOutput.violations().size());

        return new AnalysisResult(
                codeOutput.stats(),
                ruleOutput.rules().size(),
                evalOutput.violations(),
                advice);
    }

    public record AnalysisResult(
            SourceCodeParser.ParsingStats parsingStats,
            int rulesEvaluated,
            List<Violation> violations,
            AIAdvisor.AdviceResult aiAdvice) {
    }
}
