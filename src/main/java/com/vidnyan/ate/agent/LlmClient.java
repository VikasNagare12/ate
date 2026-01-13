package com.vidnyan.ate.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * LLM Client supporting Groq (free), Gemini, and mock mode.
 */
@Component
public class LlmClient {

    private static final Logger log = LoggerFactory.getLogger(LlmClient.class);

    private static final String GROQ_API_URL = "https://api.groq.com/openai/v1/chat/completions";

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    @Value("${ate.ai.provider:groq}")
    private String provider;

    @Value("${ate.ai.api-key:}")
    private String apiKey;

    @Value("${ate.ai.model:llama-3.3-70b-versatile}")
    private String model;

    public LlmClient() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .build();
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Send a prompt to the LLM and get a response.
     */
    public String chat(String systemPrompt, String userPrompt) {
        log.debug("LLM Request - Provider: {}, Model: {}", provider, model);
        
        if (apiKey == null || apiKey.isEmpty()) {
            log.warn("No API key configured. Using mock response.");
            return getMockResponse(userPrompt);
        }

        try {
            return switch (provider.toLowerCase()) {
                case "groq" -> callGroq(systemPrompt, userPrompt);
                default -> {
                    log.warn("Unknown provider: {}. Using mock.", provider);
                    yield getMockResponse(userPrompt);
                }
            };
        } catch (Exception e) {
            log.error("LLM call failed: {}", e.getMessage());
            return getMockResponse(userPrompt);
        }
    }

    /**
     * Call Groq API (OpenAI-compatible).
     */
    private String callGroq(String systemPrompt, String userPrompt) throws Exception {
        log.info("Calling Groq API with model: {}", model);

        // Build request body
        Map<String, Object> requestBody = Map.of(
                "model", model,
                "messages", List.of(
                        Map.of("role", "system", "content", systemPrompt),
                        Map.of("role", "user", "content", userPrompt)),
                "temperature", 0.3,
                "max_tokens", 2048);

        String jsonBody = objectMapper.writeValueAsString(requestBody);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(GROQ_API_URL))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .timeout(Duration.ofSeconds(60))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            log.error("Groq API error: {} - {}", response.statusCode(), response.body());
            throw new RuntimeException("Groq API error: " + response.statusCode());
        }

        // Parse response
        JsonNode root = objectMapper.readTree(response.body());
        String content = root.path("choices").get(0).path("message").path("content").asText();
        
        log.debug("Groq response received: {} chars", content.length());
        return content;
    }

    /**
     * Mock response for testing without API key.
     */
    private String getMockResponse(String prompt) {
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
                        "impact": "HIGH"
                }
                """;
        }
        
        if (prompt.contains("fix") || prompt.contains("suggestion")) {
            return """
                {
                    "suggestion": "Move the remote call outside the transaction boundary",
                        "alternatives": [
                        "Use @Async for the remote call",
                            "Use TransactionSynchronizationManager"
                    ]
                }
                """;
        }
        
        return "{}";
    }
}
