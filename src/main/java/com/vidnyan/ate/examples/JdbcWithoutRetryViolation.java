package com.vidnyan.ate.examples;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/**
 * Example: JDBC-RETRY-001 Violation
 * JDBC operations that might fail without retry mechanism.
 */
public class JdbcWithoutRetryViolation {

    private final JdbcTemplate jdbcTemplate;

    public JdbcWithoutRetryViolation(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    // VIOLATION: JDBC update without @Retryable (transient failures not handled)
    //@Transactional
    public void updateInventory(Long productId, int quantity) {
        // This could fail due to deadlock, connection timeout, etc.
        // No retry mechanism in place
        new Thread();

        jdbcTemplate.update(
            "UPDATE inventory SET quantity = quantity - ? WHERE product_id = ?",
            quantity, productId
        );
    }
    
    // Should have: @Retryable(include = {DeadlockLoserDataAccessException.class})
}
