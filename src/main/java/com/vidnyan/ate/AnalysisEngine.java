package com.vidnyan.ate;

import com.vidnyan.ate.advisor.Advice;
import com.vidnyan.ate.advisor.Advisor;
import com.vidnyan.ate.graph.CallGraph;
import com.vidnyan.ate.graph.DependencyGraph;
import com.vidnyan.ate.model.SourceModel;
import com.vidnyan.ate.model.builder.SourceModelBuilder;
import com.vidnyan.ate.parser.AstParser;
import com.vidnyan.ate.report.ReportModel;
import com.vidnyan.ate.rule.RuleDefinition;
import com.vidnyan.ate.rule.RuleEngine;
import com.vidnyan.ate.rule.Violation;
import com.vidnyan.ate.scanner.RepositoryScanner;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

/**
 * Main Analysis Engine - orchestrates the entire analysis pipeline.
 * 
 * Flow:
 * 1. Scan repository
 * 2. Parse files to AST
 * 3. Build Source Model
 * 4. Build graphs
 * 5. Load rules
 * 6. detectViolations rules
 * 7. Generate report
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AnalysisEngine implements CommandLineRunner {
    
    private final RepositoryScanner repositoryScanner;
    private final AstParser astParser;
    private final SourceModelBuilder sourceModelBuilder;
    private final RuleEngine ruleEngine;
    private final AnalysisProperties analysisProperties;
    private final Advisor advisor;
    
    /**
     * Execute the complete analysis pipeline.
     */
    public ReportModel analyze() throws IOException {
        Path repositoryRoot = Paths.get(analysisProperties.getRepositoryPath()).toAbsolutePath();
        log.info("Starting analysis of repository: {}", repositoryRoot);

        // Resolve rule files from directory
        String ruleDir = analysisProperties.getRuleDirectory();
        List<Path> ruleFiles = new java.util.ArrayList<>();

        try {
            // Try as file system path first (relative to project root or absolute)
            Path ruleDirPath = Paths.get(ruleDir);
            if (!ruleDirPath.isAbsolute()) {
                ruleDirPath = Paths.get("src/main/resources/" + ruleDir).toAbsolutePath();
            }

            if (java.nio.file.Files.exists(ruleDirPath) && java.nio.file.Files.isDirectory(ruleDirPath)) {
                log.info("Loading rules from directory: {}", ruleDirPath);
                try (java.util.stream.Stream<Path> stream = java.nio.file.Files.walk(ruleDirPath)) {
                    ruleFiles = stream
                            .filter(p -> !java.nio.file.Files.isDirectory(p))
                            .filter(p -> p.toString().endsWith(".json"))
                            .collect(java.util.stream.Collectors.toList());
                }
            } else {
                // Try as classpath resource
                log.info("Loading rules from classpath resource: {}", ruleDir);
                var url = getClass().getClassLoader().getResource(ruleDir);
                if (url != null) {
                    if (url.getProtocol().equals("file")) {
                        Path path = Paths.get(url.toURI());
                        try (java.util.stream.Stream<Path> stream = java.nio.file.Files.walk(path)) {
                            ruleFiles = stream
                                    .filter(p -> !java.nio.file.Files.isDirectory(p))
                                    .filter(p -> p.toString().endsWith(".json"))
                                    .collect(java.util.stream.Collectors.toList());
                        }
                    } else if (url.getProtocol().equals("jar")) {
                        // Simple jar handling - might need more robust solution for production
                        // For now, if we are in a jar, we might need a different approach
                        // or assume they are unpacked or listed.
                        // Given the context (local dev/execution), file system fallback is primary.
                        log.warn(
                                "JAR rule loading not fully implemented, falling back to empty list if not found on FS.");
                    }
                } else {
                    log.warn("Rule directory not found: {}", ruleDir);
                }
            }
        } catch (Exception e) {
            log.error("Error finding rule files", e);
        }
        
        log.info("Starting analysis of repository: {}", repositoryRoot);

        // Step 1: Scan repository
        log.info("Step 1: Scanning repository...");
        List<Path> sourceFiles = repositoryScanner.scanSourceFiles(repositoryRoot);
        log.info("Found {} source files", sourceFiles.size());
        
        // Step 2: Parse files to AST
        log.info("Step 2: Parsing files to AST...");
        List<AstParser.ParseResult> parseResults = astParser.parseFiles(sourceFiles);
        long successCount = parseResults.stream().filter(AstParser.ParseResult::isSuccess).count();
        log.info("Successfully parsed {}/{} files", successCount, parseResults.size());
        
        // Step 3: Build Source Model
        log.info("Step 3: Building Source Model...");
        SourceModel sourceModel = sourceModelBuilder.build(parseResults);
        log.info("Source Model built: {} types, {} methods, {} fields",
                sourceModel.getTypes().size(),
                sourceModel.getMethods().size(),
                sourceModel.getFields().size());
        
        // Step 4: Build graphs
        log.info("Step 4: Building graphs...");
        CallGraph callGraph = CallGraph.build(sourceModel);
        DependencyGraph dependencyGraph = DependencyGraph.build(sourceModel);
        log.info("Call graph: {} method relationships, Dependency graph: {} package relationships",
                callGraph.getOutgoingCalls().size(),
                dependencyGraph.getPackageDependencies().size());
        
        // Step 5: Initialize rule engine
        log.info("Step 5: Initializing rule engine...");
        ruleEngine.initialize(sourceModel, callGraph, dependencyGraph);
        
        // Step 6: Load rules
        log.info("Step 6: Loading rules...");
        List<RuleDefinition> rules = ruleEngine.loadRules(ruleFiles);
        log.info("Loaded {} rules", rules.size());
        
        // Step 7: detectViolations rules
        log.info("Step 7: Evaluating rules...");
        List<Violation> violations = ruleEngine.detectViolationsRules(rules);
        log.info("Found {} violations", violations.size());
        
        // Step 8: Generate report
        log.info("Step 8: Generating report...");
        ReportModel report = ReportModel.build(violations);
        log.info("Report generated: {} violations ({} BLOCKER, {} ERROR, {} WARN)",
                report.getSummary().getTotalViolations(),
                report.getSummary().getBlockerCount(),
                report.getSummary().getErrorCount(),
                report.getSummary().getWarnCount());
        
        return report;
    }
    
    @Override
    public void run(String... args) throws Exception {
        ReportModel report = analyze();
        
        // Print summary
        log.info("\n=== Analysis Report ===");
        log.info("Total Violations: {}", report.getSummary().getTotalViolations());
        log.info("BLOCKER: {}", report.getSummary().getBlockerCount());
        log.info("ERROR: {}", report.getSummary().getErrorCount());
        log.info("WARN: {}", report.getSummary().getWarnCount());
        log.info("Result: {}", report.getResult());
        
        // Print violations
        if (!report.getViolations().isEmpty()) {
            log.info("\n=== Violations ===");
            for (var violation : report.getViolations()) {
                log.info("[{}] {}: {}",
                        violation.getSeverity(),
                        violation.getLocation().toDisplayString(),
                        violation.getMessage());
            }
        }
        
        // AI Advisor Analysis
        log.info("\n=== AI Advisor ===");
        Advice advice = advisor.analyze(report);
        log.info("Summary: {}", advice.getSummary());
        for (Advice.Suggestion suggestion : advice.getSuggestions()) {
            log.info("[{}] {}: {}", 
                    suggestion.getImpact(), 
                    suggestion.getTitle(), 
                    suggestion.getDescription());
        }
        
        // Exit with appropriate code
        System.exit(report.getResult() == ReportModel.AnalysisResult.FAIL ? 1 : 0);
    }
}

