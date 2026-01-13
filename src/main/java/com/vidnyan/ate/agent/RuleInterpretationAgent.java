package com.vidnyan.ate.agent;

import com.vidnyan.ate.core.RuleRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * AI Agent that interprets natural language rules.
 * Converts human-readable rules into structured analysis criteria.
 */
@Component
public class RuleInterpretationAgent implements Agent<RuleRepository.Rule, RuleInterpretationAgent.InterpretedRule> {

    private static final Logger log = LoggerFactory.getLogger(RuleInterpretationAgent.class);
    
    private final LlmClient llmClient;

    public RuleInterpretationAgent(LlmClient llmClient) {
        this.llmClient = llmClient;
    }

    @Override
    public String getName() {
        return "RuleInterpretationAgent";
    }

    @Override
    public String getDescription() {
        return "Interprets natural language rules and converts them to structured analysis criteria";
    }

    @Override
    public String getSystemPrompt() {
        return """
            You are a Rule Interpretation Agent for a static code analysis system.
            Your job is to take a natural language rule definition and convert it into
            structured analysis criteria that other agents can use.
            
            For each rule, identify:
            1. The target annotation or code pattern to look for
            2. The forbidden calls or patterns that should not appear
            3. Whether to check the entire call chain or just direct calls
            4. Any exceptions or special cases
            
            Output your interpretation as JSON.
            """;
    }

    @Override
    public InterpretedRule execute(RuleRepository.Rule rule) {
        log.info("[{}] Interpreting rule: {} - {}", getName(), rule.id(), rule.name());
        
        String userPrompt = String.format("""
            Please interpret this architectural rule:
            
            Rule ID: %s
            Name: %s
            Description: %s
            
            If config is provided:
            - Annotation Required: %s
            - Sink Patterns: %s
            
            Convert this to structured analysis criteria.
            """,
            rule.id(),
            rule.name(),
            rule.description(),
            rule.config() != null ? rule.config().annotationRequired() : "none",
            rule.config() != null ? rule.config().sinkPatterns() : "none"
        );
        
        String response = llmClient.chat(getSystemPrompt(), userPrompt);
        log.debug("[{}] LLM Response: {}", getName(), response);
        
        // For now, create interpreted rule from config
        // In production, parse the LLM response
        return new InterpretedRule(
            rule.id(),
            rule.name(),
            rule.config() != null ? rule.config().annotationRequired() : null,
            rule.config() != null ? rule.config().sinkPatterns() : List.of(),
            true, // Check call chain
            rule.description()
        );
    }

    /**
     * Structured interpretation of a rule.
     */
    public record InterpretedRule(
        String ruleId,
        String ruleName,
        String targetAnnotation,
        List<String> forbiddenPatterns,
        boolean checkCallChain,
        String description
    ) {}
}
