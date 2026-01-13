package com.vidnyan.ate.api;

import com.vidnyan.ate.core.CodeAnalyzer;
import com.vidnyan.ate.core.GenAiEvaluator;
import com.vidnyan.ate.core.RuleRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.nio.file.Path;
import java.util.List;

/**
 * REST API for running analysis.
 */
@RestController
@RequestMapping("/api/analyze")
public class AnalysisController {

    private static final Logger log = LoggerFactory.getLogger(AnalysisController.class);

    private final CodeAnalyzer codeAnalyzer;
    private final GenAiEvaluator evaluator;
    private final RuleRepository ruleRepository;

    public AnalysisController(CodeAnalyzer codeAnalyzer, GenAiEvaluator evaluator, RuleRepository ruleRepository) {
        this.codeAnalyzer = codeAnalyzer;
        this.evaluator = evaluator;
        this.ruleRepository = ruleRepository;
    }

    @PostMapping
    public AnalysisResponse analyze(@RequestBody AnalysisRequest request) {
        log.info("Received analysis request for: {}", request.classesPath());

        CodeAnalyzer.AnalysisContext context = codeAnalyzer.analyze(Path.of(request.classesPath()));

        List<GenAiEvaluator.EvaluationResult> results = request.ruleIds().stream()
                .map(ruleRepository::loadRule)
                .filter(rule -> rule != null)
                .map(rule -> evaluator.evaluate(context, rule))
                .toList();

        int totalViolations = results.stream()
                .mapToInt(r -> r.violations().size())
                .sum();

        return new AnalysisResponse(
                context.classes().size(),
                request.ruleIds().size(),
                totalViolations,
                results);
    }

    @GetMapping("/health")
    public String health() {
        return "OK";
    }

    public record AnalysisRequest(String classesPath, List<String> ruleIds) {
    }

    public record AnalysisResponse(
            int classesAnalyzed,
            int rulesEvaluated,
            int totalViolations,
            List<GenAiEvaluator.EvaluationResult> results) {
    }
}
