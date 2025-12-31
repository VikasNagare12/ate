package com.vidnyan.ate;

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
import lombok.extern.slf4j.Slf4j;

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
 * 6. Evaluate rules
 * 7. Generate report
 */
@Slf4j
public class AnalysisEngine {
    
    private final Path repositoryRoot;
    private final List<Path> ruleFiles;
    
    public AnalysisEngine(Path repositoryRoot, List<Path> ruleFiles) {
        this.repositoryRoot = repositoryRoot;
        this.ruleFiles = ruleFiles;
    }
    
    /**
     * Execute the complete analysis pipeline.
     */
    public ReportModel analyze() throws IOException {
        log.info("Starting analysis of repository: {}", repositoryRoot);
        
        // Step 1: Scan repository
        log.info("Step 1: Scanning repository...");
        RepositoryScanner scanner = new RepositoryScanner(repositoryRoot);
        List<Path> sourceFiles = scanner.scanSourceFiles();
        log.info("Found {} source files", sourceFiles.size());
        
        // Step 2: Parse files to AST
        log.info("Step 2: Parsing files to AST...");
        AstParser parser = new AstParser();
        List<AstParser.ParseResult> parseResults = parser.parseFiles(sourceFiles);
        long successCount = parseResults.stream().filter(AstParser.ParseResult::isSuccess).count();
        log.info("Successfully parsed {}/{} files", successCount, parseResults.size());
        
        // Step 3: Build Source Model
        log.info("Step 3: Building Source Model...");
        SourceModelBuilder builder = new SourceModelBuilder();
        SourceModel sourceModel = builder.build(parseResults);
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
        
        // Step 5: Load rules
        log.info("Step 5: Loading rules...");
        RuleEngine ruleEngine = new RuleEngine(sourceModel, callGraph, dependencyGraph);
        List<RuleDefinition> rules = ruleEngine.loadRules(ruleFiles);
        log.info("Loaded {} rules", rules.size());
        
        // Step 6: Evaluate rules
        log.info("Step 6: Evaluating rules...");
        List<Violation> violations = ruleEngine.evaluateRules(rules);
        log.info("Found {} violations", violations.size());
        
        // Step 7: Generate report
        log.info("Step 7: Generating report...");
        ReportModel report = ReportModel.build(violations);
        log.info("Report generated: {} violations ({} BLOCKER, {} ERROR, {} WARN)",
                report.getSummary().getTotalViolations(),
                report.getSummary().getBlockerCount(),
                report.getSummary().getErrorCount(),
                report.getSummary().getWarnCount());
        
        return report;
    }
    
    /**
     * Main entry point for CLI usage.
     */
    public static void main(String[] args) throws IOException {
        if (args.length < 1) {
            System.err.println("Usage: AnalysisEngine <repository-path> [rule-files...]");
            System.exit(1);
        }
        
        Path repoPath = Paths.get(args[0]);
        List<Path> ruleFiles = args.length > 1 ?
                List.of(Paths.get(args[1])) : // Single rule file for now
                List.of(Paths.get("rules/default-rules.json")); // Default
        
        AnalysisEngine engine = new AnalysisEngine(repoPath, ruleFiles);
        ReportModel report = engine.analyze();
        
        // Print summary
        System.out.println("\n=== Analysis Report ===");
        System.out.printf("Total Violations: %d%n", report.getSummary().getTotalViolations());
        System.out.printf("BLOCKER: %d%n", report.getSummary().getBlockerCount());
        System.out.printf("ERROR: %d%n", report.getSummary().getErrorCount());
        System.out.printf("WARN: %d%n", report.getSummary().getWarnCount());
        System.out.printf("Result: %s%n", report.getResult());
        
        // Print violations
        if (!report.getViolations().isEmpty()) {
            System.out.println("\n=== Violations ===");
            for (var violation : report.getViolations()) {
                System.out.printf("[%s] %s: %s%n",
                        violation.getSeverity(),
                        violation.getLocation().toDisplayString(),
                        violation.getMessage());
            }
        }
        
        // Exit with appropriate code
        System.exit(report.getResult() == ReportModel.AnalysisResult.FAIL ? 1 : 0);
    }
}

