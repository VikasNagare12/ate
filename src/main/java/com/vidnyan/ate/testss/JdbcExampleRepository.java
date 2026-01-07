package com.vidnyan.ate.testss;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Map;

/**
 * Test examples for JDBC operations - should trigger JDBC-RETRY-001 rule
 * when these are called from @Retryable methods.
 */
@Repository
public class JdbcExampleRepository {

    private  JdbcTemplate jdbcTemplate;
    private  NamedParameterJdbcTemplate namedJdbcTemplate;


    // JdbcTemplate examples

    public String findUserById(Long id) {
        return jdbcTemplate.queryForObject(
                "SELECT name FROM users WHERE id = ?",
                String.class,
                id);
    }

    public int updateUserName(Long id, String name) {
        return jdbcTemplate.update(
                "UPDATE users SET name = ? WHERE id = ?",
                name, id);
    }

    public void createUser(String name, String email) {
        jdbcTemplate.update(
                "INSERT INTO users (name, email) VALUES (?, ?)",
                name, email);
    }

    // NamedParameterJdbcTemplate examples

    public String findUserByEmail(String email) {
        return namedJdbcTemplate.queryForObject(
                "SELECT name FROM users WHERE email = :email",
                Map.of("email", email),
                String.class);
    }

    public int updateUserEmail(Long id, String email) {
        return namedJdbcTemplate.update(
                "UPDATE users SET email = :email WHERE id = :id",
                Map.of("id", id, "email", email));
    }

    public void batchInsertUsers(String... names) {
        for (String name : names) {
            namedJdbcTemplate.update(
                    "INSERT INTO users (name) VALUES (:name)",
                    Map.of("name", name));
        }
    }
}
