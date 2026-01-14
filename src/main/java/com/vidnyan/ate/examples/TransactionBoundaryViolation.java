package com.vidnyan.ate.examples;

import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

/**
 * Example: TX-BOUNDARY-001 Violation
 * Making remote HTTP call inside @Transactional method.
 */
public class TransactionBoundaryViolation {

    private final RestTemplate restTemplate = new RestTemplate();
    
    @Transactional
    public void placeOrder(String orderId) {
        // Save to database first
        saveOrderToDatabase(orderId);
        
        // VIOLATION: HTTP call inside transaction!
        String result = restTemplate.getForObject("http://payment-service/charge/" + orderId, String.class);
        
        // More database operations
        updateOrderStatus(orderId, result);
    }
    
    private void saveOrderToDatabase(String orderId) {
        // Database save
    }
    
    private void updateOrderStatus(String orderId, String status) {
        // Database update
    }
}
