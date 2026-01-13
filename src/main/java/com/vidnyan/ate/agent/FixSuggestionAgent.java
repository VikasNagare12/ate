package com.vidnyan.ate.agent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * AI Agent that suggests fixes for code violations.
 */
@Component
public class FixSuggestionAgent implements Agent<ExplanationAgent.ExplanationResult, FixSuggestionAgent.FixResult> {

    private static final Logger log = LoggerFactory.getLogger(FixSuggestionAgent.class);
    
    private final LlmClient llmClient;

    public FixSuggestionAgent(LlmClient llmClient) {
        this.llmClient = llmClient;
    }

    @Override
    public String getName() {
        return "FixSuggestionAgent";
    }

    @Override
    public String getDescription() {
        return "Generates specific code fixes for violations";
    }

    @Override
    public String getSystemPrompt() {
        return """
            You are a Fix Suggestion Agent for a static code analysis system.
            Your job is to suggest specific, actionable fixes for code violations.
            
            For each violation:
            1. Suggest the primary fix approach
            2. Show before/after code if possible
            3. Provide alternative solutions
            4. Explain trade-offs between solutions
            
            Be practical and provide code that developers can actually use.
            """;
    }

    @Override
    public FixResult execute(ExplanationAgent.ExplanationResult explanationResult) {
        log.info("[{}] Generating fix for: {}", getName(), explanationResult.violation().methodFqn());
        
        String userPrompt = String.format("""
            Please suggest a fix for this violation:
            
            Rule: %s
            Method: %s
            Issue: %s
            
            Current code:
            ```java
            %s
            ```
            
            Explanation: %s
            
            Please provide:
            1. The recommended fix
            2. Fixed code example
            3. Alternative approaches
            """,
            explanationResult.violation().ruleId(),
            explanationResult.violation().methodFqn(),
            explanationResult.violation().reason(),
            explanationResult.violation().sourceCode(),
            explanationResult.explanation()
        );
        
        String response = llmClient.chat(getSystemPrompt(), userPrompt);
        
        return new FixResult(
            explanationResult.violation(),
            generatePrimaryFix(explanationResult),
            generateFixedCode(explanationResult),
            generateAlternatives(explanationResult)
        );
    }

    private String generatePrimaryFix(ExplanationAgent.ExplanationResult result) {
        String ruleId = result.violation().ruleId();
        
        if (ruleId.contains("TX-BOUNDARY")) {
            return "Move the remote/HTTP call outside of the @Transactional method. " +
                   "Create a separate method for the remote call and invoke it before or after the transaction.";
        }
        
        return "Refactor the code to separate concerns and avoid the flagged pattern.";
    }

    private String generateFixedCode(ExplanationAgent.ExplanationResult result) {
        String ruleId = result.violation().ruleId();
        
        if (ruleId.contains("TX-BOUNDARY")) {
            return """
                // BEFORE (violation):
                @Transactional
                public void processOrder(Order order) {
                    orderRepository.save(order);
                    paymentClient.charge(order);  // Remote call in transaction!
                }
                
                // AFTER (fixed):
                public void processOrder(Order order) {
                    PaymentResult payment = paymentClient.charge(order);  // Remote call OUTSIDE
                    saveOrderWithPayment(order, payment);                 // Then save
                }
                
                @Transactional
                private void saveOrderWithPayment(Order order, PaymentResult payment) {
                    order.setPaymentId(payment.id());
                    orderRepository.save(order);  // Only DB operations in transaction
                }
                """;
        }
        
        return "// Apply the suggested fix pattern to your code";
    }

    private List<String> generateAlternatives(ExplanationAgent.ExplanationResult result) {
        String ruleId = result.violation().ruleId();
        
        if (ruleId.contains("TX-BOUNDARY")) {
            return List.of(
                "Use @Async to execute the remote call asynchronously",
                "Use TransactionSynchronizationManager.registerSynchronization() to defer the call until after commit",
                "Use Spring's ApplicationEventPublisher to trigger the remote call after transaction completion",
                "Use the Outbox pattern: save to outbox table, process asynchronously"
            );
        }
        
        return List.of("Consider refactoring to follow the suggested pattern");
    }

    public record FixResult(
        EvaluationAgent.Violation violation,
        String primaryFix,
        String fixedCode,
        List<String> alternatives
    ) {}
}
