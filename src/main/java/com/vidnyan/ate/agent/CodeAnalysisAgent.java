package com.vidnyan.ate.agent;

import com.vidnyan.ate.analyzer.HybridAnalyzer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * AI Agent that analyzes code structure.
 * Uses the hybrid analyzer and enhances understanding with LLM.
 */
@Component
public class CodeAnalysisAgent implements Agent<CodeAnalysisAgent.AnalysisRequest, CodeAnalysisAgent.AnalysisResult> {

    private static final Logger log = LoggerFactory.getLogger(CodeAnalysisAgent.class);
    
    private final LlmClient llmClient;

    public CodeAnalysisAgent(LlmClient llmClient) {
        this.llmClient = llmClient;
    }

    @Override
    public String getName() {
        return "CodeAnalysisAgent";
    }

    @Override
    public String getDescription() {
        return "Analyzes code structure and identifies relevant patterns based on interpreted rules";
    }

    @Override
    public String getSystemPrompt() {
        return """
            You are a Code Analysis Agent for a static code analysis system.
            Your job is to analyze code context and identify methods that match
            the criteria specified by the interpreted rule.
            
            For each method, determine:
            1. Does it have the target annotation?
            2. Does it call any forbidden patterns?
            3. What is the call chain to the forbidden pattern?
            
            Be thorough but precise. Only flag actual matches.
            """;
    }

    @Override
    public AnalysisResult execute(AnalysisRequest request) {
        log.info("[{}] Analyzing code for rule: {}", getName(), request.interpretedRule().ruleId());
        
        // Get methods based on annotation OR forbidden patterns
        List<HybridAnalyzer.RichMethodContext> relevantMethods;
        String criteriaType;
        String criteriaValue;

        if (request.interpretedRule().targetAnnotation() != null) {
            relevantMethods = request.context().getMethodsWithAnnotation(request.interpretedRule().targetAnnotation());
            criteriaType = "annotation";
            criteriaValue = "@" + request.interpretedRule().targetAnnotation();
        } else {
            // If no annotation, look for methods that call the forbidden patterns (Sinks)
            relevantMethods = request.context().getMethodsCallingAny(request.interpretedRule().forbiddenPatterns());
            criteriaType = "calls to";
            criteriaValue = request.interpretedRule().forbiddenPatterns().toString();
        }
        
        log.info("[{}] Found {} methods with {}",
                getName(), relevantMethods.size(), criteriaValue);
        
        // Build context for LLM
        StringBuilder methodsContext = new StringBuilder();
        for (HybridAnalyzer.RichMethodContext method : relevantMethods) {
            methodsContext.append(method.toLlmContext()).append("\n---\n");
        }
        
        String userPrompt = String.format("""
            Analyze these methods for rule: %s
            
            Target annotation: @%s
            Forbidden patterns: %s
            
            Methods to analyze:
            %s
            
            Identify which methods have the annotation and call forbidden patterns.
            """,
            request.interpretedRule().ruleId(),
            request.interpretedRule().targetAnnotation(),
            request.interpretedRule().forbiddenPatterns(),
            methodsContext.toString()
        );
        
        String response = llmClient.chat(getSystemPrompt(), userPrompt);
        log.debug("[{}] LLM Response: {}", getName(), response);
        
        return new AnalysisResult(
            request.interpretedRule(),
            relevantMethods,
            response
        );
    }

    public record AnalysisRequest(
        HybridAnalyzer.HybridAnalysisResult context,
        RuleInterpretationAgent.InterpretedRule interpretedRule
    ) {}

    public record AnalysisResult(
        RuleInterpretationAgent.InterpretedRule rule,
        List<HybridAnalyzer.RichMethodContext> relevantMethods,
        String llmAnalysis
    ) {}
}
