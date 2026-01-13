package com.vidnyan.ate;

import com.vidnyan.ate.agent.Orchestrator;
import com.vidnyan.ate.analyzer.HybridAnalyzer;
import com.vidnyan.ate.core.RuleRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.List;

/**
 * Command Line Runner that executes the multi-agent analysis.
 */
@Component
public class AnalysisRunner implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(AnalysisRunner.class);

    private final HybridAnalyzer hybridAnalyzer;
    private final Orchestrator orchestrator;
    private final RuleRepository ruleRepository;

    @Value("${ate.analysis.source-path:}")
    private String sourcePath;

    @Value("${ate.analysis.classes-path:}")
    private String classesPath;

    @Value("${ate.analysis.enabled:true}")
    private boolean analysisEnabled;

    public AnalysisRunner(HybridAnalyzer hybridAnalyzer, Orchestrator orchestrator, RuleRepository ruleRepository) {
        this.hybridAnalyzer = hybridAnalyzer;
        this.orchestrator = orchestrator;
        this.ruleRepository = ruleRepository;
    }

    @Override
    public void run(String... args) throws Exception {
        if (!analysisEnabled) {
            log.info("Analysis is disabled. Set ate.analysis.enabled=true to run.");
            return;
        }

        String source = getArgOrConfig(args, 0, sourcePath);
        String classes = getArgOrConfig(args, 1, classesPath);

        if (source.isEmpty() || classes.isEmpty()) {
            printUsage();
            return;
        }

        log.info("");
        log.info("╔══════════════════════════════════════════════════════════════╗");
        log.info("║     ATE - AI AGENTS FOR STATIC CODE ANALYSIS                 ║");
        log.info("║     Powered by: SootUp + JavaParser + Gen AI                 ║");
        log.info("╚══════════════════════════════════════════════════════════════╝");
        log.info("");
        log.info("Source path:  {}", source);
        log.info("Classes path: {}", classes);

        // Phase 1: Hybrid code analysis (SootUp + JavaParser)
        log.info("");
        log.info("═══ PHASE 1: CODE ANALYSIS (SootUp + JavaParser) ═══");
        HybridAnalyzer.HybridAnalysisResult codeContext = hybridAnalyzer.analyze(
            Path.of(source),
            Path.of(classes)
        );
        log.info("Methods analyzed: {}", codeContext.methods().size());

        // Phase 2: Load rules
        log.info("");
        log.info("═══ PHASE 2: LOADING RULES ═══");
        List<RuleRepository.Rule> rules = ruleRepository.loadAllRules();
        log.info("Rules loaded: {}", rules.size());
        rules.forEach(r -> log.info("  - {}: {}", r.id(), r.name()));

        // Phase 3: Multi-Agent Analysis
        log.info("");
        log.info("═══ PHASE 3: MULTI-AGENT ANALYSIS ═══");
        Orchestrator.AnalysisReport report = orchestrator.analyze(codeContext, rules);

        // Phase 4: Print detailed report
        log.info("");
        printReport(report);
    }

    private void printReport(Orchestrator.AnalysisReport report) {
        log.info("╔══════════════════════════════════════════════════════════════╗");
        log.info("║                    ANALYSIS REPORT                           ║");
        log.info("╚══════════════════════════════════════════════════════════════╝");

        for (Orchestrator.RuleReport ruleReport : report.ruleReports()) {
            log.info("");
            log.info("Rule: {} - {}", ruleReport.rule().id(), ruleReport.rule().name());
            log.info("Violations: {}", ruleReport.violations().size());

            for (Orchestrator.ViolationReport v : ruleReport.violations()) {
                log.info("");
                log.warn("  ⚠️ VIOLATION: {}", v.violation().methodFqn());
                log.warn("     Line: {}", v.violation().lineNumber());
                log.warn("     Issue: {}", v.violation().reason());
                log.info("");
                log.info("     📖 EXPLANATION:");
                log.info("     {}", v.explanation().explanation());
                log.info("");
                log.info("     🔧 SUGGESTED FIX:");
                log.info("     {}", v.fix().primaryFix());
                log.info("");
                log.info("     Alternative approaches:");
                v.fix().alternatives().forEach(alt -> log.info("       • {}", alt));
            }
        }

        log.info("");
        log.info("═══════════════════════════════════════════════════════════════");
        log.info("SUMMARY: {} rules, {} violations", report.rulesProcessed(), report.totalViolations());
        log.info("═══════════════════════════════════════════════════════════════");
    }

    private String getArgOrConfig(String[] args, int index, String configValue) {
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
    }
}
