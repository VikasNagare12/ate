package com.vidnyan.ate.examples;

import org.springframework.retry.annotation.Retryable;
import org.springframework.web.client.RestTemplate;

/**
 * Example: REMOTE-RETRY-001 Violation
 * Non-idempotent remote calls with @Retryable.
 */
public class RemoteRetryViolation {

    private final RestTemplate restTemplate = new RestTemplate();

    // VIOLATION: POST request (non-idempotent) with @Retryable
    @Retryable
    public void createPayment(String paymentId, double amount) {
        restTemplate.postForObject(
            "http://payment-service/payments",
            new PaymentRequest(paymentId, amount),
            String.class
        );
    }
    
    // This is OK - GET is idempotent
    @Retryable
    public String getPaymentStatus(String paymentId) {
        return restTemplate.getForObject(
            "http://payment-service/payments/" + paymentId,
            String.class
        );
    }

    record PaymentRequest(String id, double amount) {}
}
