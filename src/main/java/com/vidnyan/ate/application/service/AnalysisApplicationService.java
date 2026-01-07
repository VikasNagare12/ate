package com.vidnyan.ate.application.service;

import com.vidnyan.ate.application.port.in.AnalyzeCodeUseCase;
import com.vidnyan.ate.application.port.out.AIAdvisor;
import com.vidnyan.ate.application.port.out.RuleRepository;
import com.vidnyan.ate.application.port.out.SourceCodeParser;
import com.vidnyan.ate.domain.graph.CallGraph;
import com.vidnyan.ate.domain.graph.DependencyGraph;
import com.vidnyan.ate.domain.model.SourceModel;
import com.vidnyan.ate.domain.rule.*;
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
    
    private final SourceCodeParser sourceCodeParser;
    private final RuleRepository ruleRepository;
    private final List<RuleEvaluator> ruleEvaluators;
    private final AIAdvisor aiAdvisor;
    
    @Override
    public AnalysisResult analyze(AnalysisRequest request) {
        Instant startTime = Instant.now();
        log.info("Starting analysis of: {}", request.sourcePath());
        
        // Step 1: Parse source code
        log.info("Step 1: Parsing source code...");
        SourceCodeParser.ParsingResult parsingResult = sourceCodeParser.parse(
                request.sourcePath(),
                new SourceCodeParser.ParsingOptions(
                        request.includeTests(),
                        true, // resolve symbols
                        List.of()
                )
        );
        
        SourceModel sourceModel = parsingResult.sourceModel();
        log.info("Parsed: {} types, {} methods", 
                sourceModel.types().size(), 
                sourceModel.methods().size());
        
        // Step 2: Build graphs
        log.info("Step 2: Building graphs...");
        CallGraph callGraph = CallGraph.build(sourceModel, parsingResult.callEdges());
        DependencyGraph dependencyGraph = DependencyGraph.build(sourceModel);
        
        log.info("Built: {} call edges, {} packages", 
                callGraph.stats().edgeCount(),
                dependencyGraph.stats().packageCount());
        
        // Step 3: Load rules
        log.info("Step 3: Loading rules...");
        List<RuleDefinition> rules = request.ruleIds().isEmpty() 
                ? ruleRepository.findEnabled()
                : request.ruleIds().stream()
                        .map(ruleRepository::findById)
                        .filter(java.util.Optional::isPresent)
                        .map(java.util.Optional::get)
                        .toList();
        
        log.info("Loaded {} rules", rules.size());
        
        // Step 4: Evaluate rules
        log.info("Step 4: Evaluating rules...");
        List<EvaluationResult> ruleResults = new ArrayList<>();
        List<Violation> allViolations = new ArrayList<>();
        
        for (RuleDefinition rule : rules) {
                log.info("  Processing rule: {}", rule.id());
            EvaluationContext context = EvaluationContext.of(
                    rule, sourceModel, callGraph, dependencyGraph);
            
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
        
        // Step 5: Get AI advice
        log.info("Step 5: Getting AI advice...");
        AIAdvisor.AdviceResult advice = aiAdvisor.getAdvice(allViolations);
        log.info("AI Summary: {}", advice.summary());
        
        // Build result
        Duration totalDuration = Duration.between(startTime, Instant.now());
        AnalysisStats stats = new AnalysisStats(
                parsingResult.stats().filesProcessed(),
                sourceModel.types().size(),
                sourceModel.methods().size(),
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
