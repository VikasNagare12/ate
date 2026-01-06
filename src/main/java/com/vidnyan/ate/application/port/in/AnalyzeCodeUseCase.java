package com.vidnyan.ate.application.port.in;

import com.vidnyan.ate.domain.rule.EvaluationResult;
import com.vidnyan.ate.domain.rule.RuleDefinition;
import com.vidnyan.ate.domain.rule.Violation;

import java.nio.file.Path;
import java.util.List;

/**
 * Primary use case: Analyze code for architectural violations.
 * This is the main entry point to the application.
 */
public interface AnalyzeCodeUseCase {
    
    /**
     * Analyze a codebase and return all violations.
     * @param request Analysis request parameters
     * @return Analysis result with violations and metadata
     */
    AnalysisResult analyze(AnalysisRequest request);
    
    /**
     * Analysis request parameters.
     */
    record AnalysisRequest(
        Path sourcePath,
        List<String> ruleIds,    // Empty = all rules
        boolean includeTests,
        int maxDepth
    ) {
        public static AnalysisRequest forPath(Path path) {
            return new AnalysisRequest(path, List.of(), false, 100);
        }
    }
    
    /**
     * Analysis result.
     */
    record AnalysisResult(
        List<Violation> violations,
        List<EvaluationResult> ruleResults,
        AnalysisStats stats
    ) {
        public boolean hasCriticalViolations() {
            return violations.stream()
                    .anyMatch(v -> v.severity() == RuleDefinition.Severity.BLOCKER);
        }
        
        public int violationCount(RuleDefinition.Severity severity) {
            return (int) violations.stream()
                    .filter(v -> v.severity() == severity)
                    .count();
        }
    }
    
    /**
     * Analysis statistics.
     */
    record AnalysisStats(
        int filesAnalyzed,
        int typesAnalyzed,
        int methodsAnalyzed,
        int rulesEvaluated,
        long totalDurationMs
    ) {}
}
