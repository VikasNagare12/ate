package com.vidnyan.ate.adapter.in.cli;

import com.vidnyan.ate.application.port.in.AnalyzeCodeUseCase;
import com.vidnyan.ate.application.port.in.AnalyzeCodeUseCase.AnalysisRequest;
import com.vidnyan.ate.application.port.in.AnalyzeCodeUseCase.AnalysisResult;
import com.vidnyan.ate.application.port.out.AIAdvisor;
import com.vidnyan.ate.domain.rule.RuleDefinition;
import com.vidnyan.ate.domain.rule.Violation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.List;

/**
 * CLI Runner for standalone code analysis.
 * Runs analysis when ate.analyze.path property is set.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AnalysisCliRunner implements CommandLineRunner {

    private final AnalyzeCodeUseCase analyzeCodeUseCase;
    private final AIAdvisor aiAdvisor;
    
    @Value("${ate.analyze.path:}")
    private String sourcePath;

    private final org.springframework.context.ConfigurableApplicationContext context;

    @Override
    public void run(String... args) throws Exception {
        if (sourcePath == null || sourcePath.isBlank()) {
            log.info("No source path specified. Set ate.analyze.path property.");
            return;
        }

        try {
            log.info("╔══════════════════════════════════════════════════════════════╗");
            log.info("║           ATE - Architectural Transaction Engine              ║");
            log.info("╠══════════════════════════════════════════════════════════════╣");
            log.info("║ Analyzing: {}", truncatePath(sourcePath, 50));
            log.info("╚══════════════════════════════════════════════════════════════╝");

            // Run analysis
            AnalysisRequest request = AnalysisRequest.forPath(Path.of(sourcePath));
            AnalysisResult result = analyzeCodeUseCase.analyze(request);

            // Print results
            printResults(result);

            // Print AI advice
            if (!result.violations().isEmpty()) {
                AIAdvisor.AdviceResult advice = aiAdvisor.getAdvice(result.violations());
                printAdvice(advice);
            }

            log.info("");
            log.info("Analysis complete!");
        } finally {
            // Ensure application shuts down after analysis
            org.springframework.boot.SpringApplication.exit(context, () -> 0);
        }
    }

    private void printResults(AnalysisResult result) {
        log.info("");
        log.info("═══════════════════════════════════════════════════════════════");
        log.info(" ANALYSIS RESULTS");
        log.info("═══════════════════════════════════════════════════════════════");
        log.info(" Files analyzed:   {}", result.stats().filesAnalyzed());
        log.info(" Types analyzed:   {}", result.stats().typesAnalyzed());
        log.info(" Methods analyzed: {}", result.stats().methodsAnalyzed());
        log.info(" Rules evaluated:  {}", result.stats().rulesEvaluated());
        log.info(" Duration:         {}ms", result.stats().totalDurationMs());
        log.info("───────────────────────────────────────────────────────────────");

        int blockers = result.violationCount(RuleDefinition.Severity.BLOCKER);
        int errors = result.violationCount(RuleDefinition.Severity.ERROR);
        int warnings = result.violationCount(RuleDefinition.Severity.WARN);
        int infos = result.violationCount(RuleDefinition.Severity.INFO);

        log.info(" VIOLATIONS:");
        log.info("   🔴 Blockers: {}", blockers);
        log.info("   🟠 Errors:   {}", errors);
        log.info("   🟡 Warnings: {}", warnings);
        log.info("   🔵 Info:     {}", infos);
        log.info("═══════════════════════════════════════════════════════════════");

        if (result.violations().isEmpty()) {
            log.info("");
            log.info("✅ No violations found! Your code is clean.");
            return;
        }

        log.info("");
        log.info(" VIOLATION DETAILS:");
        log.info("───────────────────────────────────────────────────────────────");

        int count = 0;
        for (Violation v : result.violations()) {
            count++;
            if (count > 100) {
                log.info(" ... and {} more violations", result.violations().size() - 100);
                break;
            }

            String severity = switch (v.severity()) {
                case BLOCKER -> "🔴 BLOCKER";
                case ERROR -> "🟠 ERROR";
                case WARN -> "🟡 WARN";
                case INFO -> "🔵 INFO";
            };

            log.info("");
            log.info(" {} [{}]", severity, v.ruleId());
            log.info(" Location: {}", v.location() != null ? v.location().format() : "unknown");
            log.info(" Message:  {}", v.message());
            if (v.callChain() != null && !v.callChain().isEmpty()) {
                log.info(" Chain:    {}", v.formattedCallChain());
            }
        }
    }

    private void printAdvice(AIAdvisor.AdviceResult advice) {
        log.info("");
        log.info("═══════════════════════════════════════════════════════════════");
        log.info(" AI ADVISOR RECOMMENDATIONS");
        log.info("═══════════════════════════════════════════════════════════════");
        log.info(" {}", advice.summary());
        log.info("───────────────────────────────────────────────────────────────");

        for (AIAdvisor.Recommendation rec : advice.recommendations()) {
            log.info("");
            log.info(" 💡 {}", rec.explanation());
            log.info("    Fix: {}", rec.suggestedFix());
        }

        log.info("");
        log.info(" ⚠️  {}", advice.disclaimer());
    }

    private String truncatePath(String path, int maxLen) {
        if (path.length() <= maxLen)
            return path;
        return "..." + path.substring(path.length() - maxLen + 3);
    }
}
