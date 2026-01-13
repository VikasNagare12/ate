package com.vidnyan.ate.agent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * LLM Client for making API calls to Gen AI providers.
 * Supports Gemini and OpenAI.
 */
@Component
public class LlmClient {

    private static final Logger log = LoggerFactory.getLogger(LlmClient.class);

    @Value("${ate.ai.provider:gemini}")
    private String provider;

    @Value("${ate.ai.api-key:}")
    private String apiKey;

    @Value("${ate.ai.model:gemini-pro}")
    private String model;

    /**
     * Send a prompt to the LLM and get a response.
     */
    public String chat(String systemPrompt, String userPrompt) {
        log.debug("LLM Request - Provider: {}, Model: {}", provider, model);
        log.debug("System Prompt: {} chars", systemPrompt.length());
        log.debug("User Prompt: {} chars", userPrompt.length());

        // TODO: Implement actual API calls
        // For now, return a placeholder that indicates the LLM should respond
        
        if (apiKey == null || apiKey.isEmpty()) {
            log.warn("No API key configured. Using mock response.");
            return getMockResponse(userPrompt);
        }

        // In production, implement:
        // - Gemini: https://generativelanguage.googleapis.com/v1beta/models/gemini-pro:generateContent
        // - OpenAI: https://api.openai.com/v1/chat/completions
        
        return getMockResponse(userPrompt);
    }

    private String getMockResponse(String prompt) {
        // Return intelligent mock responses based on prompt content
        if (prompt.contains("interpret") || prompt.contains("rule")) {
            return """
                {
                    "interpretation": {
                        "targetAnnotation": "Transactional",
                        "forbiddenCalls": ["RestTemplate", "WebClient", "HttpClient"],
                        "checkCallChain": true
                    }
                }
                """;
        }
        
        if (prompt.contains("evaluate") || prompt.contains("violation")) {
            return """
                {
                    "violations": [
                        {
                            "method": "com.example.OrderService.placeOrder",
                            "reason": "Calls RestTemplate.postForObject within @Transactional method",
                            "severity": "HIGH"
                        }
                    ]
                }
                """;
        }
        
        if (prompt.contains("explain")) {
            return """
                {
                    "explanation": "This method makes an HTTP call inside a database transaction. 
                    If the HTTP call is slow or fails, the database transaction will be held open, 
                    potentially causing connection pool exhaustion and deadlocks.",
                    "impact": "HIGH",
                    "affectedAreas": ["Database connections", "Application responsiveness"]
                }
                """;
        }
        
        if (prompt.contains("fix") || prompt.contains("suggestion")) {
            return """
                {
                    "suggestion": "Move the remote call outside the transaction boundary",
                    "codeChange": {
                        "before": "@Transactional\\npublic void save() { remoteCall(); dbSave(); }",
                        "after": "public void save() { remoteCall(); saveInternal(); }\\n@Transactional\\nprivate void saveInternal() { dbSave(); }"
                    },
                    "alternativeSolutions": [
                        "Use @Async for the remote call",
                        "Use TransactionSynchronizationManager to defer the call"
                    ]
                }
                """;
        }
        
        return "{}";
    }
}
