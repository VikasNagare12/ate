package com.vidnyan.ate.examples;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/**
 * Example: SAME-TABLE-UPDATE-001 Violation
 * Multiple updates to the same table in single transaction.
 */
public class SameTableUpdateViolation {

    private final JdbcTemplate jdbcTemplate;

    public SameTableUpdateViolation(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    // VIOLATION: Multiple updates to same table (users) in one transaction
    @Transactional
    public void updateUserProfile(Long userId, String name, String email, String phone) {
        // First update
        jdbcTemplate.update("UPDATE users SET name = ? WHERE id = ?", name, userId);
        
        // Second update to SAME table
        jdbcTemplate.update("UPDATE users SET email = ? WHERE id = ?", email, userId);
        
        // Third update to SAME table
        jdbcTemplate.update("UPDATE users SET phone = ? WHERE id = ?", phone, userId);
        
        // Should be: single UPDATE with all fields
    }
}
