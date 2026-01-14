package com.vidnyan.ate.examples;

import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Example: JDBC-TX-001 Violation
 * JDBC operations without @Transactional annotation.
 */
public class JdbcWithoutTransactionViolation {

    private final JdbcTemplate jdbcTemplate;

    public JdbcWithoutTransactionViolation(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    // VIOLATION: No @Transactional but calling jdbcTemplate.update
    public void updateUserStatus(Long userId, String status) {
        jdbcTemplate.update("UPDATE users SET status = ? WHERE id = ?", status, userId);
    }
    
    // VIOLATION: Multiple JDBC operations without transaction
    public void transferFunds(Long fromAccount, Long toAccount, double amount) {
        jdbcTemplate.update("UPDATE accounts SET balance = balance - ? WHERE id = ?", amount, fromAccount);
        jdbcTemplate.update("UPDATE accounts SET balance = balance + ? WHERE id = ?", amount, toAccount);
    }
}
