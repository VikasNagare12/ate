package com.vidnyan.ate.examples;

import org.springframework.scheduling.annotation.Async;
import org.springframework.transaction.annotation.Transactional;

/**
 * Example: ASYNC-TX-001 Violation
 * @Async method called from @Transactional loses transaction context.
 */
public class AsyncTransactionViolation {

    // VIOLATION: Calling async method from transactional method
    @Transactional
    public void processOrder(String orderId) {
        saveOrder(orderId);
        
        // This async call loses the transaction context!
        sendNotificationAsync(orderId);
        
        // If this fails, the notification was already sent
        completeOrder(orderId);
    }
    
    @Async
    public void sendNotificationAsync(String orderId) {
        // This runs in a separate thread WITHOUT the transaction
        System.out.println("Sending notification for: " + orderId);
    }
    
    private void saveOrder(String orderId) {
        // Database operation
    }
    
    private void completeOrder(String orderId) {
        // Database operation
    }
}
