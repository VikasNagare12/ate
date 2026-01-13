package com.vidnyan.ate.core;

import com.vidnyan.ate.analyzer.HybridAnalyzer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Gen AI Evaluator using rich hybrid context.
 * Sends comprehensive code information to LLM for accurate analysis.
 */
@Component
public class GenAiEvaluator {

    private static final Logger log = LoggerFactory.getLogger(GenAiEvaluator.class);

    @Value("${ate.ai.enabled:true}")
    private boolean aiEnabled;

    /**
     * Evaluate a rule using hybrid analysis context.
     */
    public EvaluationResult evaluate(HybridAnalyzer.HybridAnalysisResult context, RuleRepository.Rule rule) {
        log.info("Evaluating rule: {} - {}", rule.id(), rule.name());

        // Build rich prompt
        String prompt = buildRichPrompt(context, rule);
        log.debug("Prompt built: {} characters", prompt.length());

        // Call LLM
        String response = callLlm(prompt);

        // Parse violations
        List<Violation> violations = parseViolations(response, rule);

        return new EvaluationResult(rule.id(), violations);
    }

    /**
     * Build a rich prompt with full context for accurate LLM analysis.
     */
    private String buildRichPrompt(HybridAnalyzer.HybridAnalysisResult context, RuleRepository.Rule rule) {
        StringBuilder sb = new StringBuilder();

        // System instruction
        sb.append("# Architectural Rule Analysis\n\n");
        sb.append("You are an expert software architect analyzing Java code for architectural violations.\n\n");

        // Rule definition
        sb.append("## Rule to Check\n");
        sb.append("- **ID**: ").append(rule.id()).append("\n");
        sb.append("- **Name**: ").append(rule.name()).append("\n");
        sb.append("- **Description**: ").append(rule.description()).append("\n");
        sb.append("- **Severity**: ").append(rule.severity()).append("\n\n");

        // Find relevant methods
        sb.append("## Methods to Analyze\n\n");

        if (rule.config() != null && rule.config().annotationRequired() != null) {
            String annotation = rule.config().annotationRequired();

            List<HybridAnalyzer.RichMethodContext> relevantMethods = context.getMethodsWithAnnotation(annotation);

            if (relevantMethods.isEmpty()) {
                sb.append("No methods found with @").append(annotation).append(" annotation.\n");
            } else {
                sb.append("Found ").append(relevantMethods.size())
                        .append(" methods with @").append(annotation).append(":\n\n");

                relevantMethods.stream()
                        .limit(10) // Limit to avoid token overflow
                        .forEach(method -> sb.append(method.toLlmContext()).append("\n---\n"));
            }
        }

        // Sink patterns to look for
        if (rule.config() != null && rule.config().sinkPatterns() != null) {
            sb.append("\n## Sink Patterns (Forbidden Calls)\n");
            rule.config().sinkPatterns().forEach(pattern -> sb.append("- ").append(pattern).append("\n"));
        }

        // Expected output
        sb.append("\n## Your Task\n");
        sb.append("1. For each method, check if it (or its call chain) calls any of the sink patterns.\n");
        sb.append("2. If a method with the required annotation calls a forbidden sink, it's a violation.\n");
        sb.append("3. Return your analysis as JSON:\n");
        sb.append("```json\n");
        sb.append("[\n");
        sb.append("  {\n");
        sb.append("    \"method\": \"fully.qualified.MethodName\",\n");
        sb.append("    \"violates\": true,\n");
        sb.append("    \"reason\": \"Calls RestTemplate.postForObject which is a remote HTTP call\",\n");
        sb.append("    \"callChain\": [\"method1\", \"method2\", \"sinkMethod\"],\n");
        sb.append("    \"suggestion\": \"Move the remote call outside the transaction\"\n");
        sb.append("  }\n");
        sb.append("]\n");
        sb.append("```\n");
        sb.append("If no violations, return empty array: []\n");

        return sb.toString();
    }

    private String callLlm(String prompt) {
        log.info("Sending {} chars to LLM", prompt.length());
        // TODO: Implement actual LLM API call (Gemini/OpenAI)
        // For now, log the prompt and return empty
        log.debug("Prompt:\n{}", prompt);
        return "[]";
    }

    private List<Violation> parseViolations(String response, RuleRepository.Rule rule) {
        // TODO: Parse JSON response from LLM
        return List.of();
    }

    public record EvaluationResult(String ruleId, List<Violation> violations) {
        public boolean hasViolations() {
            return !violations.isEmpty();
        }
    }

    public record Violation(
            String ruleId,
            String methodName,
            String message,
            List<String> callChain,
            String suggestion) {
    }
}
