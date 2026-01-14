package com.vidnyan.ate.agent;

import com.vidnyan.ate.analyzer.HybridAnalyzer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * AI Agent that evaluates code against rules to find violations.
 */
@Component
public class EvaluationAgent implements Agent<CodeAnalysisAgent.AnalysisResult, EvaluationAgent.EvaluationResult> {

    private static final Logger log = LoggerFactory.getLogger(EvaluationAgent.class);
    
    private final LlmClient llmClient;

    public EvaluationAgent(LlmClient llmClient) {
        this.llmClient = llmClient;
    }

    @Override
    public String getName() {
        return "EvaluationAgent";
    }

    @Override
    public String getDescription() {
        return "Evaluates analyzed code against rules to determine violations";
    }

    @Override
    public String getSystemPrompt() {
        return """
            You are an Evaluation Agent for a static code analysis system.
            Your job is to determine whether code violates architectural rules.
            
            For each method analyzed:
            1. Check if it has the required annotation
            2. Check if it calls any forbidden patterns
            3. If both are true, it's a VIOLATION
            
            Be precise. A violation only occurs when BOTH conditions are met.
            Return your findings as JSON with a list of violations.
            """;
    }

    @Override
    public EvaluationResult execute(CodeAnalysisAgent.AnalysisResult analysisResult) {
        log.info("[{}] Evaluating {} methods for violations", getName(), analysisResult.relevantMethods().size());
        
        List<Violation> violations = new ArrayList<>();
        
        for (HybridAnalyzer.RichMethodContext method : analysisResult.relevantMethods()) {
            String matchedPattern = findForbiddenPattern(method, analysisResult.rule().forbiddenPatterns());
            
            if (matchedPattern != null) {
                violations.add(new Violation(
                    analysisResult.rule().ruleId(),
                    method.fullyQualifiedName(),
                    "Method calls forbidden pattern: " + matchedPattern,
                    matchedPattern,
                    method.sourceCode(),
                    method.startLine()
                ));
                log.warn("[{}] VIOLATION: {} calls {}", getName(), method.methodName(), matchedPattern);
            }
        }
        
        // Consult LLM for more nuanced evaluation
        String userPrompt = String.format("""
            Evaluate these findings:
            
            Rule: %s - %s
            Violations found by static analysis: %d
            
            Please confirm or refine these violations.
            Are there any false positives or missed violations?
            """,
            analysisResult.rule().ruleId(),
            analysisResult.rule().description(),
            violations.size()
        );
        
        String llmEvaluation = llmClient.chat(getSystemPrompt(), userPrompt);
        
        return new EvaluationResult(
            analysisResult.rule().ruleId(),
            violations,
            llmEvaluation
        );
    }

    /**
     * Find if method calls any forbidden pattern.
     * Checks: direct calls, deep call chain, and source code.
     */
    private String findForbiddenPattern(HybridAnalyzer.RichMethodContext method, List<String> forbiddenPatterns) {
        if (forbiddenPatterns == null || forbiddenPatterns.isEmpty()) {
            return null;
        }

        for (String pattern : forbiddenPatterns) {
            // Extract simple class name for matching (e.g., "RestTemplate" from
            // "org.springframework.web.client.RestTemplate")
            String simpleName = extractSimpleName(pattern);

            // Check 1: Direct calls from JavaParser
            for (String call : method.directCalls()) {
                if (matchesPattern(call, pattern, simpleName)) {
                    log.debug("Found match in direct call: {} matches {}", call, pattern);
                    return call;
                }
            }

            // Check 2: Deep call chain from SootUp
            for (String call : method.deepCallChain()) {
                if (matchesPattern(call, pattern, simpleName)) {
                    log.debug("Found match in deep call: {} matches {}", call, pattern);
                    return call;
                }
            }

            // Check 3: Source code contains the pattern (most reliable for examples)
            String sourceCode = method.sourceCode();
            if (sourceCode != null) {
                if (sourceCode.contains(simpleName) || sourceCode.contains(pattern)) {
                    log.debug("Found match in source code: {} contains {}", method.methodName(), simpleName);
                    return simpleName + " (in source)";
                }
            }
        }

        return null;
    }

    private boolean matchesPattern(String call, String fullPattern, String simpleName) {
        if (call == null)
            return false;
        // Match full pattern or simple name
        return call.contains(fullPattern) || call.contains(simpleName);
    }

    private String extractSimpleName(String fqn) {
        if (fqn == null)
            return "";
        int lastDot = fqn.lastIndexOf('.');
        return lastDot >= 0 ? fqn.substring(lastDot + 1) : fqn;
    }

    public record EvaluationResult(
        String ruleId,
        List<Violation> violations,
        String llmEvaluation
    ) {
        public boolean hasViolations() {
            return !violations.isEmpty();
        }
    }

    public record Violation(
        String ruleId,
        String methodFqn,
        String reason,
        String matchedPattern,
        String sourceCode,
        int lineNumber
    ) {}
}
