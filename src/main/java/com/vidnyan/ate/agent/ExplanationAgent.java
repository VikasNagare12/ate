package com.vidnyan.ate.agent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * AI Agent that generates human-readable explanations for violations.
 */
@Component
public class ExplanationAgent implements Agent<EvaluationAgent.Violation, ExplanationAgent.ExplanationResult> {

    private static final Logger log = LoggerFactory.getLogger(ExplanationAgent.class);
    
    private final LlmClient llmClient;

    public ExplanationAgent(LlmClient llmClient) {
        this.llmClient = llmClient;
    }

    @Override
    public String getName() {
        return "ExplanationAgent";
    }

    @Override
    public String getDescription() {
        return "Generates clear, actionable explanations for why code violates rules";
    }

    @Override
    public String getSystemPrompt() {
        return """
            You are an Explanation Agent for a static code analysis system.
            Your job is to explain WHY a code violation is problematic.
            
            For each violation:
            1. Explain the technical reason it's a problem
            2. Describe the potential consequences
            3. Provide context that helps developers understand the risk
            
            Be clear, concise, and educational. Avoid jargon where possible.
            """;
    }

    @Override
    public ExplanationResult execute(EvaluationAgent.Violation violation) {
        log.info("[{}] Generating explanation for: {}", getName(), violation.methodFqn());
        
        String userPrompt = String.format("""
            Please explain this code violation:
            
            Rule: %s
            Method: %s
            Line: %d
            Issue: %s
            
            Source code:
            ```java
            %s
            ```
            
            Explain:
            1. Why is this a problem?
            2. What could go wrong?
            3. What should the developer understand?
            """,
            violation.ruleId(),
            violation.methodFqn(),
            violation.lineNumber(),
            violation.reason(),
            violation.sourceCode()
        );
        
        String response = llmClient.chat(getSystemPrompt(), userPrompt);
        
        // Parse response (in production, use proper JSON parsing)
        return new ExplanationResult(
            violation,
            parseExplanation(response),
            parseImpact(response),
            parseConsequences(response)
        );
    }

    private String parseExplanation(String response) {
        // In production, parse JSON. For now, return a meaningful default.
        if (response.contains("transaction")) {
            return "This method makes an external call (HTTP/remote service) inside a database transaction. " +
                   "This is problematic because the transaction will remain open while waiting for the " +
                   "external call to complete, potentially holding database locks for an extended period.";
        }
        return "This code pattern violates the architectural rule. Please review the code and apply the suggested fix.";
    }

    private String parseImpact(String response) {
        return "HIGH - Could cause performance degradation and system instability";
    }

    private List<String> parseConsequences(String response) {
        return List.of(
            "Database connection pool exhaustion",
            "Increased transaction timeout risk",
            "Potential deadlocks under high load"
        );
    }

    public record ExplanationResult(
        EvaluationAgent.Violation violation,
        String explanation,
        String impact,
        List<String> consequences
    ) {}
}
