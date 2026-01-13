package com.vidnyan.ate.api;

import com.vidnyan.ate.analyzer.HybridAnalyzer;
import com.vidnyan.ate.core.GenAiEvaluator;
import com.vidnyan.ate.core.RuleRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.nio.file.Path;
import java.util.List;

/**
 * REST API for running hybrid analysis.
 */
@RestController
@RequestMapping("/api/analyze")
public class AnalysisController {

    private static final Logger log = LoggerFactory.getLogger(AnalysisController.class);
    
    private final HybridAnalyzer hybridAnalyzer;
    private final GenAiEvaluator evaluator;
    private final RuleRepository ruleRepository;

    public AnalysisController(HybridAnalyzer hybridAnalyzer, GenAiEvaluator evaluator, RuleRepository ruleRepository) {
        this.hybridAnalyzer = hybridAnalyzer;
        this.evaluator = evaluator;
        this.ruleRepository = ruleRepository;
    }

    @PostMapping
    public AnalysisResponse analyze(@RequestBody AnalysisRequest request) {
        log.info("Received hybrid analysis request");
        log.info("  Source path: {}", request.sourcePath());
        log.info("  Classes path: {}", request.classesPath());
        log.info("  Rules: {}", request.ruleIds());
        
        // 1. Hybrid analysis (SootUp + JavaParser)
        HybridAnalyzer.HybridAnalysisResult context = hybridAnalyzer.analyze(
            Path.of(request.sourcePath()),
            Path.of(request.classesPath())
        );
        
        // 2. Load and evaluate rules
        List<GenAiEvaluator.EvaluationResult> results = request.ruleIds().stream()
            .map(ruleRepository::loadRule)
            .filter(rule -> rule != null)
            .map(rule -> evaluator.evaluate(context, rule))
            .toList();
        
        // 3. Summarize
        int totalViolations = results.stream()
            .mapToInt(r -> r.violations().size())
            .sum();
        
        return new AnalysisResponse(
            context.methods().size(),
            request.ruleIds().size(),
            totalViolations,
            results
        );
    }

    @GetMapping("/health")
    public String health() {
        return "OK - Ultimate Hybrid Analyzer (SootUp + JavaParser + Gen AI)";
    }

    public record AnalysisRequest(
        String sourcePath,      // Path to .java source files
        String classesPath,     // Path to compiled .class files
        List<String> ruleIds
    ) {}
    
    public record AnalysisResponse(
        int methodsAnalyzed,
        int rulesEvaluated,
        int totalViolations,
        List<GenAiEvaluator.EvaluationResult> results
    ) {}
}
