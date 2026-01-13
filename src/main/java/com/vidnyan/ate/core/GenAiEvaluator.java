package com.vidnyan.ate.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Gen AI-powered rule evaluator.
 */
@Component
public class GenAiEvaluator {

    private static final Logger log = LoggerFactory.getLogger(GenAiEvaluator.class);

    @Value("${ate.ai.enabled:true}")
    private boolean aiEnabled;

    /**
     * Evaluate a rule against analyzed code.
     */
    public EvaluationResult evaluate(CodeAnalyzer.AnalysisContext context, RuleRepository.Rule rule) {
        log.info("Evaluating rule: {} - {}", rule.id(), rule.name());

        // Build prompt from code context
        String prompt = buildPrompt(context, rule);

        // Call LLM
        String response = callLlm(prompt);

        // Parse violations
        List<Violation> violations = parseViolations(response, rule);

        return new EvaluationResult(rule.id(), violations);
    }

    private String buildPrompt(CodeAnalyzer.AnalysisContext context, RuleRepository.Rule rule) {
        StringBuilder sb = new StringBuilder();

        sb.append("# Architectural Rule Analysis\n\n");
        sb.append("## Rule\n");
        sb.append("- ID: ").append(rule.id()).append("\n");
        sb.append("- Name: ").append(rule.name()).append("\n");
        sb.append("- Description: ").append(rule.description()).append("\n\n");

        sb.append("## Code Context\n\n");

        // Find relevant methods
        if (rule.config() != null && rule.config().annotationRequired() != null) {
            String annotation = rule.config().annotationRequired();

            context.classes().stream()
                    .flatMap(c -> c.getMethods().stream())
                    .filter(m -> m.getAnnotations().stream()
                            .anyMatch(a -> a.getRawType().getSimpleName().equals(annotation)))
                    .limit(20)
                    .forEach(method -> {
                        sb.append("### Method: ").append(method.getFullName()).append("\n");
                        sb.append("Annotations: ").append(context.getAnnotations(method)).append("\n");
                        sb.append("Calls:\n");
                        context.getOutgoingCalls(method).stream()
                                .limit(10)
                                .forEach(call -> sb.append("  - ").append(call.callee()).append("\n"));
                        sb.append("\n");
                    });
        }

        sb.append("## Task\n");
        sb.append("Identify violations. Return JSON: [{\"method\": \"...\", \"reason\": \"...\"}]\n");

        return sb.toString();
    }

    private String callLlm(String prompt) {
        log.debug("Prompt length: {} chars", prompt.length());
        // TODO: Implement actual LLM API call
        return "[]";
    }

    private List<Violation> parseViolations(String response, RuleRepository.Rule rule) {
        // TODO: Implement JSON parsing
        return List.of();
    }

    public record EvaluationResult(String ruleId, List<Violation> violations) {
        public boolean hasViolations() {
            return !violations.isEmpty();
        }
    }

    public record Violation(String ruleId, String methodName, String message, int lineNumber) {
    }
}
