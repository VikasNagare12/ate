package com.vidnyan.ate.testss;

import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;

/**
 * Test service that calls JDBC methods - should trigger violations
 * if JDBC calls are made from @Retryable methods.
 */
@Service
public class JdbcExampleService1 {

    private final JdbcExampleService jdbcExampleService;

    public JdbcExampleService1(JdbcExampleService jdbcExampleService) {
        this.jdbcExampleService = jdbcExampleService;
    }
    
    // This should trigger JDBC-RETRY-001 - JDBC inside @Retryable
//    @Retryable
//    public String getUserName(Long id) {
//        return repository.findUserById(id);
//    }
//
    // This should also trigger - NamedJdbcTemplate inside @Retryable
   // @Retryable
//    public String getUserByEmail(String email) {
//        return repository.findUserByEmail(email);
//    }
//
    // This is fine - no
    @Retryable
    public void createUser(String name, String email) {
        jdbcExampleService.createUser("test", "test@example.com");
    }
    
    // This should trigger - update inside @Retryable
   // @Retryable
   // public int updateUser(Long id, String name) {
//        return repository.updateUserName(id, name);
//    }
}
