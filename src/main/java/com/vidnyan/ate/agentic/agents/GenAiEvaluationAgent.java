package com.vidnyan.ate.agentic.agents;

import com.vidnyan.ate.agentic.core.Agent;
import com.vidnyan.ate.domain.graph.CallEdge;
import com.vidnyan.ate.domain.graph.CallGraph;
import com.vidnyan.ate.domain.model.MethodEntity;
import com.vidnyan.ate.domain.model.SourceModel;
import com.vidnyan.ate.domain.rule.RuleDefinition;
import com.vidnyan.ate.domain.rule.Violation;
import com.vidnyan.ate.domain.model.Location;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Gen AI-powered Evaluation Agent.
 * Uses LLM to evaluate rules against code structure without needing coded
 * evaluators.
 * 
 * This agent:
 * 1. Takes the code structure (SourceModel, CallGraph) and rule definitions
 * 2. Builds a prompt for the LLM describing the code and the rule
 * 3. Sends the prompt to the LLM
 * 4. Parses the LLM response to extract violations
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GenAiEvaluationAgent implements Agent<GenAiEvaluationAgent.Input, GenAiEvaluationAgent.Output> {

    @Value("${ate.ai.enabled:true}")
    private boolean aiEnabled;

    @Override
    public String getName() {
        return "GenAiEvaluationAgent";
    }

    @Override
    public Output execute(Input input) {
        log.info("[{}] Evaluating {} rules using Gen AI...", getName(), input.rules().size());

        List<Violation> allViolations = new ArrayList<>();

        for (RuleDefinition rule : input.rules()) {
            log.info("[{}] Analyzing rule: {} - {}", getName(), rule.id(), rule.name());

            // Build context prompt
            String prompt = buildPrompt(rule, input.sourceModel(), input.callGraph());

            // Call LLM (placeholder for actual API call)
            String llmResponse = callLlm(prompt);

            // Parse response to extract violations
            List<Violation> ruleViolations = parseViolations(llmResponse, rule);
            allViolations.addAll(ruleViolations);

            log.info("[{}] Rule {} found {} violations via AI.",
                    getName(), rule.id(), ruleViolations.size());
        }

        log.info("[{}] Gen AI evaluation complete. Total violations: {}", getName(), allViolations.size());

        return new Output(allViolations);
    }

    /**
     * Build a prompt for the LLM to evaluate a specific rule.
     */
    private String buildPrompt(RuleDefinition rule, SourceModel model, CallGraph graph) {
        StringBuilder sb = new StringBuilder();

        // System instruction
        sb.append(
                "You are a software architecture analysis AI. Your task is to find violations of architectural rules in Java code.\n\n");

        // Rule definition
        sb.append("## RULE TO CHECK\n");
        sb.append("Rule ID: ").append(rule.id()).append("\n");
        sb.append("Rule Name: ").append(rule.name()).append("\n");
        sb.append("Description: ").append(rule.description()).append("\n");
        sb.append("Severity: ").append(rule.severity()).append("\n\n");

        // Code context - Methods with relevant annotations
        sb.append("## CODE CONTEXT\n");
        sb.append("Here are the methods in the codebase:\n\n");

        // Find relevant methods (e.g., those with @Transactional for TX rules)
        model.methods().values().stream()
                .filter(m -> hasRelevantAnnotations(m, rule))
                .limit(20) // Limit to avoid token overflow
                .forEach(method -> {
                    sb.append("### Method: ").append(method.fullyQualifiedName()).append("\n");
                    sb.append("Annotations: ").append(
                            method.annotations().stream()
                                    .map(a -> "@" + a.simpleName())
                                    .collect(Collectors.joining(", ")))
                            .append("\n");

                    // Add outgoing calls
                    List<CallEdge> calls = graph.getOutgoingCalls(method.fullyQualifiedName());
                    if (!calls.isEmpty()) {
                        sb.append("Calls:\n");
                        calls.stream().limit(10).forEach(edge -> {
                            sb.append("  - ").append(edge.effectiveCalleeFqn()).append("\n");
                        });
                    }
                    sb.append("\n");
                });

        // Expected output format
        sb.append("## YOUR TASK\n");
        sb.append("Analyze the code above and identify any violations of the rule.\n");
        sb.append("For each violation, output a JSON object with:\n");
        sb.append("- methodFqn: The fully qualified name of the violating method\n");
        sb.append("- message: A clear explanation of why this violates the rule\n");
        sb.append("- suggestion: How to fix it\n\n");
        sb.append("Output format: JSON array of violations. If no violations, output empty array [].\n");

        return sb.toString();
    }

    /**
     * Check if a method has annotations relevant to the rule.
     */
    private boolean hasRelevantAnnotations(MethodEntity method, RuleDefinition rule) {
        // For TX-BOUNDARY rules, look for @Transactional
        if (rule.id().contains("TX-BOUNDARY")) {
            return method.annotations().stream()
                    .anyMatch(a -> a.fullyQualifiedName().contains("Transactional"));
        }
        // For RETRY rules, look for @Retryable
        if (rule.id().contains("RETRY")) {
            return method.annotations().stream()
                    .anyMatch(a -> a.fullyQualifiedName().contains("Retry"));
        }
        // Default: include all public methods
        return method.modifiers().contains(com.vidnyan.ate.domain.model.TypeEntity.Modifier.PUBLIC);
    }

    /**
     * Call the LLM API with the prompt.
     * This is a placeholder - implement with actual API (Gemini, OpenAI, etc.)
     */
    private String callLlm(String prompt) {
        log.debug("[{}] Sending prompt to LLM ({} chars)", getName(), prompt.length());

        // TODO: Implement actual LLM API call
        // For now, return mock response for demonstration

        // In production, use RestTemplate/WebClient to call:
        // - Gemini:
        // https://generativelanguage.googleapis.com/v1beta/models/gemini-pro:generateContent
        // - OpenAI: https://api.openai.com/v1/chat/completions

        return """
                [
                    {
                        "methodFqn": "com.example.OrderService#placeOrder()",
                        "message": "Method with @Transactional calls RestTemplate.getForObject which makes a remote HTTP call",
                        "suggestion": "Move the remote call outside the transaction or use @Async"
                    }
                ]
                """;
    }

    /**
     * Parse LLM response to extract violations.
     */
    private List<Violation> parseViolations(String llmResponse, RuleDefinition rule) {
        List<Violation> violations = new ArrayList<>();

        // Simple JSON parsing (in production, use Jackson/Gson)
        // This is a basic implementation for demonstration

        if (llmResponse.contains("methodFqn")) {
            // Extract violations from JSON response
            // For now, create a mock violation based on the response

            // In production, properly parse the JSON:
            // ObjectMapper mapper = new ObjectMapper();
            // List<ViolationDto> dtos = mapper.readValue(llmResponse, new TypeReference<>()
            // {});

            Violation v = Violation.builder()
                    .ruleId(rule.id())
                    .ruleName(rule.name())
                    .severity(rule.severity())
                    .message("AI-detected: Method may violate " + rule.name())
                    .location(Location.at("unknown", 0, 0))
                    .callChain(List.of("Detected by Gen AI analysis"))
                    .build();
            violations.add(v);
        }

        return violations;
    }

    public record Input(
            SourceModel sourceModel,
            CallGraph callGraph,
            List<RuleDefinition> rules) {
    }

    public record Output(List<Violation> violations) {
    }
}
