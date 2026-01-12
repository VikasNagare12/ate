package com.vidnyan.ate.testss;

import org.springframework.jdbc.core.JdbcTemplate;

import org.springframework.transaction.annotation.Transactional;

public class SameTableUpdateTestService {

    private final JdbcTemplate jdbcTemplate;

    public SameTableUpdateTestService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    public void badTransaction() {
        // VIOLATION: Two updates to 'users' table
        updateUser("alice");
        updateUser("bob");
    }

    @Transactional
    public void goodTransaction() {
        // OK: Updates to different tables
        updateUser("charlie");
        updateAccount("charlie");
    }

    @Transactional
    public void newGoodTransaction() { // Renamed to avoid conflict with existing goodTransaction
        jdbcTemplate.update("update users set name = ?", "Alice");
        jdbcTemplate.update("update accounts set balance = ?", 100);
    }

    @Transactional
    public void layeredTransaction() {
        String query = "update users set status = 'ACTIVE'";
        // This transaction updates 'users' twice, but passing the query down 3 layers
        passQueryLayer1(query);
        passQueryLayer1(query);
    }

    private void passQueryLayer1(String sql) {
        passQueryLayer2(sql);
    }

    private void passQueryLayer2(String sql) {
        passQueryLayer3(sql);
    }

    private void passQueryLayer3(String sql) {
        // The evaluator currently sees "sql" as the argument, not the original string
        jdbcTemplate.update(sql);
    }

    @Transactional
    public void mixedTransaction() {
        // VIOLATION: Two inserts into 'orders'
        jdbcTemplate.update("INSERT INTO orders (id) VALUES (?)", 1);
        jdbcTemplate.update("UPDATE accounts SET balance = ? WHERE id = ?", 100, 1);
        jdbcTemplate.update("INSERT INTO orders (id) VALUES (?)", 2);
    }

    private void updateUser(String name) {
        jdbcTemplate.update("UPDATE users SET name = ? WHERE id = ?", name, 1);
    }

    private void updateAccount(String user) {
        jdbcTemplate.update("UPDATE accounts SET status = ? WHERE user = ?", "ACTIVE", user);
    }
}
