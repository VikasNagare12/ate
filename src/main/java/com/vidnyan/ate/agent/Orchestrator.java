package com.vidnyan.ate.agent;

import com.vidnyan.ate.analyzer.HybridAnalyzer;
import com.vidnyan.ate.core.RuleRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Orchestrator that coordinates all AI agents.
 * Manages the multi-agent workflow for code analysis.
 */
@Component
public class Orchestrator {

    private static final Logger log = LoggerFactory.getLogger(Orchestrator.class);

    private final RuleInterpretationAgent ruleAgent;
    private final CodeAnalysisAgent codeAgent;
    private final EvaluationAgent evalAgent;
    private final ExplanationAgent explainAgent;
    private final FixSuggestionAgent fixAgent;

    public Orchestrator(
            RuleInterpretationAgent ruleAgent,
            CodeAnalysisAgent codeAgent,
            EvaluationAgent evalAgent,
            ExplanationAgent explainAgent,
            FixSuggestionAgent fixAgent) {
        this.ruleAgent = ruleAgent;
        this.codeAgent = codeAgent;
        this.evalAgent = evalAgent;
        this.explainAgent = explainAgent;
        this.fixAgent = fixAgent;
    }

    /**
     * Execute the full multi-agent analysis workflow.
     */
    public AnalysisReport analyze(HybridAnalyzer.HybridAnalysisResult codeContext, List<RuleRepository.Rule> rules) {
        log.info("╔══════════════════════════════════════════════════════════════╗");
        log.info("║           MULTI-AGENT ANALYSIS WORKFLOW                      ║");
        log.info("╚══════════════════════════════════════════════════════════════╝");
        
        List<RuleReport> ruleReports = new ArrayList<>();
        
        for (RuleRepository.Rule rule : rules) {
            log.info("");
            log.info("━━━ Processing Rule: {} ━━━", rule.id());
            
            // Step 1: Rule Interpretation Agent
            log.info("[Step 1/5] {} - Interpreting rule...", ruleAgent.getName());
            RuleInterpretationAgent.InterpretedRule interpretedRule = ruleAgent.execute(rule);
            
            // Step 2: Code Analysis Agent
            log.info("[Step 2/5] {} - Analyzing code...", codeAgent.getName());
            CodeAnalysisAgent.AnalysisResult analysisResult = codeAgent.execute(
                new CodeAnalysisAgent.AnalysisRequest(codeContext, interpretedRule)
            );
            
            // Step 3: Evaluation Agent
            log.info("[Step 3/5] {} - Evaluating violations...", evalAgent.getName());
            EvaluationAgent.EvaluationResult evalResult = evalAgent.execute(analysisResult);
            
            // Step 4 & 5: For each violation, get explanation and fix
            List<ViolationReport> violationReports = new ArrayList<>();
            
            for (EvaluationAgent.Violation violation : evalResult.violations()) {
                log.info("[Step 4/5] {} - Explaining violation...", explainAgent.getName());
                ExplanationAgent.ExplanationResult explanation = explainAgent.execute(violation);
                
                log.info("[Step 5/5] {} - Generating fix...", fixAgent.getName());
                FixSuggestionAgent.FixResult fix = fixAgent.execute(explanation);
                
                violationReports.add(new ViolationReport(violation, explanation, fix));
            }
            
            ruleReports.add(new RuleReport(rule, interpretedRule, evalResult, violationReports));
            
            log.info("━━━ Rule {} complete: {} violations ━━━", rule.id(), evalResult.violations().size());
        }
        
        int totalViolations = ruleReports.stream()
            .mapToInt(r -> r.violations().size())
            .sum();
        
        log.info("");
        log.info("═══════════════════════════════════════════════════════════════");
        log.info("         MULTI-AGENT ANALYSIS COMPLETE                         ");
        log.info("═══════════════════════════════════════════════════════════════");
        log.info("  Rules processed:  {}", rules.size());
        log.info("  Total violations: {}", totalViolations);
        log.info("═══════════════════════════════════════════════════════════════");
        
        return new AnalysisReport(rules.size(), totalViolations, ruleReports);
    }

    /**
     * Complete analysis report.
     */
    public record AnalysisReport(
        int rulesProcessed,
        int totalViolations,
        List<RuleReport> ruleReports
    ) {}

    /**
     * Report for a single rule.
     */
    public record RuleReport(
        RuleRepository.Rule rule,
        RuleInterpretationAgent.InterpretedRule interpretedRule,
        EvaluationAgent.EvaluationResult evaluationResult,
        List<ViolationReport> violations
    ) {}

    /**
     * Complete report for a single violation.
     */
    public record ViolationReport(
        EvaluationAgent.Violation violation,
        ExplanationAgent.ExplanationResult explanation,
        FixSuggestionAgent.FixResult fix
    ) {}
}
