package com.vidnyan.ate.application.service;

import com.vidnyan.ate.analyzer.SootUpAnalyzer;
import com.vidnyan.ate.analyzer.SootUpEvaluationContext;
import com.vidnyan.ate.application.port.in.AnalyzeCodeUseCase;
import com.vidnyan.ate.application.port.out.AIAdvisor;
import com.vidnyan.ate.application.port.out.RuleRepository;
import com.vidnyan.ate.domain.rule.*;
import sootup.core.views.View;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Main application service that orchestrates the analysis workflow.
 * Implements the primary use case.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AnalysisApplicationService implements AnalyzeCodeUseCase {
    
    // Legacy parser removed
    private final RuleRepository ruleRepository;
    private final List<RuleEvaluator> ruleEvaluators;
    private final AIAdvisor aiAdvisor;
    
    @Override
    public AnalysisResult analyze(AnalysisRequest request) {
        Instant startTime = Instant.now();
        log.info("Starting analysis of: {}", request.sourcePath());
        
        // Step 1: Initialize SootUp Analyzer
        log.info("Step 1: Initializing SootUp Analyzer...");
        // Assuming compiled classes are same as source root for now (Maven structure)
        // In real CLI usage we might need separate paths, but for this refactor we
        // assume standard Maven layout
        SootUpAnalyzer analyzer = new SootUpAnalyzer(request.sourcePath(), request.sourcePath());
        View baseView = analyzer.analyze();

        log.info("SootUp View created with {} classes", baseView.getClasses().size());
        
        // Step 2: Load rules
        log.info("Step 2: Loading rules...");
        List<RuleDefinition> rules = request.ruleIds().isEmpty() 
                ? ruleRepository.findEnabled()
                : request.ruleIds().stream()
                        .map(ruleRepository::findById)
                        .filter(java.util.Optional::isPresent)
                        .map(java.util.Optional::get)
                        .toList();
        
        log.info("Loaded {} rules", rules.size());
        
        // Step 3: Evaluate rules
        log.info("Step 3: Evaluating rules...");
        List<EvaluationResult> ruleResults = new ArrayList<>();
        List<Violation> allViolations = new ArrayList<>();
        
        for (RuleDefinition rule : rules) {
                log.info("  Processing rule: {}", rule.id());

                // Create context with specific rule
                SootUpEvaluationContext context = new SootUpEvaluationContext(baseView, rule);
            
            // Find matching evaluator
            RuleEvaluator evaluator = findEvaluator(rule);
            if (evaluator != null) {
                    log.info("    Found evaluator: {}", evaluator.getClass().getSimpleName());
                try {
                    EvaluationResult result = evaluator.evaluate(context);
                    ruleResults.add(result);
                    allViolations.addAll(result.violations());
                    
                    if (result.hasViolations()) {
                        log.info("  {} found {} violations", 
                                rule.id(), result.violationCount());
                    }
                } catch (Exception e) {
                    log.error("Error evaluating rule {}: {}", rule.id(), e.getMessage());
                    ruleResults.add(EvaluationResult.error(rule.id(), e.getMessage()));
                }
            } else {
                log.warn("No evaluator found for rule: {}", rule.id());
                ruleResults.add(EvaluationResult.skipped(rule.id(), "No evaluator available"));
            }
        }
        
        // Step 4: Get AI advice
        log.info("Step 4: Getting AI advice...");
        AIAdvisor.AdviceResult advice = aiAdvisor.getAdvice(allViolations);
        log.info("AI Summary: {}", advice.summary());
        
        // Build result
        Duration totalDuration = Duration.between(startTime, Instant.now());
        AnalysisStats stats = new AnalysisStats(
                baseView.getClasses().size(), // parsingResult files -> classes
                baseView.getClasses().size(), // types
                baseView.getClasses().stream().mapToLong(c -> c.getMethods().size()).sum(), // methods
                rules.size(),
                totalDuration.toMillis()
        );
        
        log.info("Analysis complete: {} violations in {}ms", 
                allViolations.size(), stats.totalDurationMs());
        
        return new AnalysisResult(allViolations, ruleResults, stats);
    }
    
    private RuleEvaluator findEvaluator(RuleDefinition rule) {
        return ruleEvaluators.stream()
                .filter(e -> e.supports(rule))
                .findFirst()
                .orElse(null);
    }
}
