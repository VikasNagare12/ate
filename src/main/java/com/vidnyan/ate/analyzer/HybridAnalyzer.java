package com.vidnyan.ate.analyzer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.*;

/**
 * Combines analysis results from SootUp and JavaParser.
 * Creates rich context for LLM evaluation.
 */
@Component
public class HybridAnalyzer {

    private static final Logger log = LoggerFactory.getLogger(HybridAnalyzer.class);

    private final SootUpAnalyzer sootUpAnalyzer;
    private final JavaParserAnalyzer javaParserAnalyzer;

    public HybridAnalyzer(SootUpAnalyzer sootUpAnalyzer, JavaParserAnalyzer javaParserAnalyzer) {
        this.sootUpAnalyzer = sootUpAnalyzer;
        this.javaParserAnalyzer = javaParserAnalyzer;
    }

    /**
     * Perform full analysis combining all sources.
     */
    public HybridAnalysisResult analyze(Path sourcePath, Path classesPath) {
        log.info("=== Hybrid Analysis Started ===");
        log.info("Source path: {}", sourcePath);
        log.info("Classes path: {}", classesPath);

        // 1. Analyze source with JavaParser (for AST, source code)
        JavaParserAnalyzer.SourceAnalysisResult sourceResult = javaParserAnalyzer.analyze(sourcePath);
        log.info("JavaParser: {} methods parsed", sourceResult.methods().size());

        // 2. Analyze bytecode with SootUp (for call graph)
        SootUpAnalyzer.CallGraphResult callGraphResult = sootUpAnalyzer.analyze(classesPath);
        log.info("SootUp: {} methods analyzed", callGraphResult.methods().size());

        // 3. Merge results
        Map<String, RichMethodContext> mergedMethods = new HashMap<>();
        
        for (var entry : sourceResult.methods().entrySet()) {
            String fqn = entry.getKey();
            JavaParserAnalyzer.MethodInfo sourceInfo = entry.getValue();
            
            // Find matching SootUp data
            List<String> deepCallChain = callGraphResult.getDeepCallChain(fqn, 5);
            
            mergedMethods.put(fqn, new RichMethodContext(
                fqn,
                sourceInfo.className(),
                sourceInfo.methodName(),
                sourceInfo.annotations(),
                sourceInfo.directCalls(),
                deepCallChain,
                sourceInfo.sourceCode(),
                sourceInfo.filePath(),
                sourceInfo.startLine(),
                sourceInfo.endLine()
            ));
        }

        log.info("=== Hybrid Analysis Complete: {} methods ===", mergedMethods.size());
        return new HybridAnalysisResult(mergedMethods, sourceResult, callGraphResult);
    }

    /**
     * Combined analysis result.
     */
    public record HybridAnalysisResult(
        Map<String, RichMethodContext> methods,
        JavaParserAnalyzer.SourceAnalysisResult sourceAnalysis,
        SootUpAnalyzer.CallGraphResult callGraphAnalysis
    ) {
        public List<RichMethodContext> getMethodsWithAnnotation(String annotationName) {
            return methods.values().stream()
                .filter(m -> m.annotations().stream()
                    .anyMatch(a -> a.contains(annotationName)))
                .toList();
        }
        
        public Optional<RichMethodContext> getMethod(String fqn) {
            return Optional.ofNullable(methods.get(fqn));
        }
    }

    /**
     * Rich method context combining all analysis sources.
     */
    public record RichMethodContext(
        String fullyQualifiedName,
        String className,
        String methodName,
        List<String> annotations,
        List<String> directCalls,       // From JavaParser
        List<String> deepCallChain,     // From SootUp
        String sourceCode,              // From JavaParser
        String filePath,
        int startLine,
        int endLine
    ) {
        /**
         * Build a rich context string for LLM.
         */
        public String toLlmContext() {
            StringBuilder sb = new StringBuilder();
            sb.append("### Method: ").append(fullyQualifiedName).append("\n");
            sb.append("**File**: ").append(filePath).append(" (lines ").append(startLine).append("-").append(endLine).append(")\n");
            sb.append("**Annotations**: ").append(String.join(", ", annotations)).append("\n");
            
            sb.append("\n**Direct Calls**:\n");
            directCalls.forEach(call -> sb.append("  - ").append(call).append("\n"));
            
            if (!deepCallChain.isEmpty()) {
                sb.append("\n**Deep Call Chain** (transitive calls):\n");
                deepCallChain.forEach(call -> sb.append("  → ").append(call).append("\n"));
            }
            
            sb.append("\n**Source Code**:\n```java\n").append(sourceCode).append("\n```\n");
            
            return sb.toString();
        }
    }
}
