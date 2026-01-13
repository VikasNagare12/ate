package com.vidnyan.ate;

import com.vidnyan.ate.analyzer.HybridAnalyzer;
import com.vidnyan.ate.core.GenAiEvaluator;
import com.vidnyan.ate.core.RuleRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.List;

/**
 * Command Line Runner for analysis.
 * Loads all rules and processes the target codebase on startup.
 */
@Component
public class AnalysisRunner implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(AnalysisRunner.class);

    private final HybridAnalyzer hybridAnalyzer;
    private final GenAiEvaluator evaluator;
    private final RuleRepository ruleRepository;

    @Value("${ate.analysis.source-path:}")
    private String sourcePath;

    @Value("${ate.analysis.classes-path:}")
    private String classesPath;

    @Value("${ate.analysis.enabled:true}")
    private boolean analysisEnabled;

    public AnalysisRunner(HybridAnalyzer hybridAnalyzer, GenAiEvaluator evaluator, RuleRepository ruleRepository) {
        this.hybridAnalyzer = hybridAnalyzer;
        this.evaluator = evaluator;
        this.ruleRepository = ruleRepository;
    }

    @Override
    public void run(String... args) throws Exception {
        if (!analysisEnabled) {
            log.info("Analysis is disabled. Set ate.analysis.enabled=true to run.");
            return;
        }

        // Parse command line arguments or use config
        String source = getArgOrConfig(args, 0, sourcePath, "source-path");
        String classes = getArgOrConfig(args, 1, classesPath, "classes-path");

        if (source.isEmpty() || classes.isEmpty()) {
            printUsage();
            return;
        }

        log.info("╔══════════════════════════════════════════════════════════════╗");
        log.info("║     ATE - Ultimate Hybrid Static Analysis Engine             ║");
        log.info("║     SootUp + JavaParser + Gen AI                             ║");
        log.info("╚══════════════════════════════════════════════════════════════╝");
        log.info("");
        log.info("Source path:  {}", source);
        log.info("Classes path: {}", classes);
        log.info("");

        // 1. Perform hybrid analysis
        log.info("=== Phase 1: Hybrid Analysis (JavaParser + SootUp) ===");
        HybridAnalyzer.HybridAnalysisResult context = hybridAnalyzer.analyze(
            Path.of(source),
            Path.of(classes)
        );
        log.info("Analyzed {} methods", context.methods().size());

        // 2. Load all rules
        log.info("");
        log.info("=== Phase 2: Loading Rules ===");
        List<RuleRepository.Rule> rules = ruleRepository.loadAllRules();
        log.info("Loaded {} rules", rules.size());

        // 3. Evaluate each rule
        log.info("");
        log.info("=== Phase 3: Rule Evaluation (Gen AI) ===");
        int totalViolations = 0;
        
        for (RuleRepository.Rule rule : rules) {
            log.info("");
            log.info("Evaluating: {} - {}", rule.id(), rule.name());
            
            GenAiEvaluator.EvaluationResult result = evaluator.evaluate(context, rule);
            
            if (result.hasViolations()) {
                log.warn("  FOUND {} violations!", result.violations().size());
                totalViolations += result.violations().size();
                
                for (GenAiEvaluator.Violation v : result.violations()) {
                    log.warn("    - {}: {}", v.methodName(), v.message());
                }
            } else {
                log.info("  No violations found.");
            }
        }

        // 4. Summary
        log.info("");
        log.info("═══════════════════════════════════════════════════════════════");
        log.info("                      ANALYSIS COMPLETE                         ");
        log.info("═══════════════════════════════════════════════════════════════");
        log.info("  Methods analyzed: {}", context.methods().size());
        log.info("  Rules evaluated:  {}", rules.size());
        log.info("  Total violations: {}", totalViolations);
        log.info("═══════════════════════════════════════════════════════════════");
    }

    private String getArgOrConfig(String[] args, int index, String configValue, String name) {
        if (args.length > index && !args[index].isEmpty()) {
            return args[index];
        }
        return configValue != null ? configValue : "";
    }

    private void printUsage() {
        log.info("");
        log.info("Usage: java -jar ate.jar <source-path> <classes-path>");
        log.info("");
        log.info("Or configure in application.properties:");
        log.info("  ate.analysis.source-path=/path/to/src/main/java");
        log.info("  ate.analysis.classes-path=/path/to/target/classes");
        log.info("");
        log.info("Example:");
        log.info("  java -jar ate.jar ./src/main/java ./target/classes");
        log.info("");
    }
}
