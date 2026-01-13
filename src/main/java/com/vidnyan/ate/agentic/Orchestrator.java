package com.vidnyan.ate.agentic;

import com.vidnyan.ate.agentic.agents.CodeStructureAgent;
import com.vidnyan.ate.agentic.agents.GenAiEvaluationAgent;
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
 * 
 * Uses Gen AI for rule evaluation - no coded evaluators needed!
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class Orchestrator {

    private final CodeStructureAgent codeStructureAgent;
    private final RuleDefinitionAgent ruleDefinitionAgent;
    private final GenAiEvaluationAgent genAiEvaluationAgent;
    private final AIAdvisor aiAdvisor;

    /**
     * Execute a full analysis workflow using Gen AI.
     */
    public AnalysisResult analyze(Path sourcePath, List<String> ruleIds) {
            log.info("=== Agentic AI Analysis Started ===");
        log.info("Source: {}", sourcePath);
        log.info("Mode: Gen AI-Powered Evaluation");

        // Phase 1: Code Structure Analysis
        log.info("Phase 1: CodeStructureAgent - Parsing source code...");
        CodeStructureAgent.Output codeOutput = codeStructureAgent.execute(
                new CodeStructureAgent.Input(sourcePath));
        log.info("  -> Found {} types, {} methods",
                        codeOutput.sourceModel().types().size(),
                        codeOutput.sourceModel().methods().size());

        // Phase 2: Rule Loading
        log.info("Phase 2: RuleDefinitionAgent - Loading rules...");
        RuleDefinitionAgent.Output ruleOutput = ruleDefinitionAgent.execute(
                new RuleDefinitionAgent.Input(ruleIds));
        log.info("  -> Loaded {} rules", ruleOutput.rules().size());

        // Phase 3: Gen AI Evaluation
        log.info("Phase 3: GenAiEvaluationAgent - Evaluating with LLM...");
        GenAiEvaluationAgent.Output evalOutput = genAiEvaluationAgent.execute(
                        new GenAiEvaluationAgent.Input(
                        codeOutput.sourceModel(),
                        codeOutput.callGraph(),
                        ruleOutput.rules()));
        log.info("  -> Found {} violations", evalOutput.violations().size());

        // Phase 4 (Optional): AI Advice
        AIAdvisor.AdviceResult advice = null;
        if (!evalOutput.violations().isEmpty()) {
                log.info("Phase 4: AIAdvisor - Generating fix suggestions...");
            advice = aiAdvisor.getAdvice(evalOutput.violations());
        }

        log.info("=== Agentic AI Analysis Complete ===");

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
