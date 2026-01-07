package com.vidnyan.ate.testss;

import org.springframework.retry.annotation.Retryable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * Test service that calls JDBC methods - should trigger violations
 * if JDBC calls are made from @Retryable methods.
 */
@Service
public class JdbcExampleService {

    private final JdbcExampleRepository repository;

    public JdbcExampleService(JdbcExampleRepository repository) {
        this.repository = repository;
    }

    // This should trigger JDBC-RETRY-001 - JDBC inside @Retryable
    @Retryable
    public String getUserName(Long id) {
        return repository.findUserById(id);
    }

    // This should also trigger - NamedJdbcTemplate inside @Retryable
    @Retryable
    public String getUserByEmail(String email) {
        return repository.findUserByEmail(email);
    }

    // This is fine - no@Retryable
    @Async
    public void createUser(String name, String email) {
        repository.createUser(name, email);
    }

    // This should trigger - update inside @Retryable
    @Retryable
    public int updateUser(Long id, String name) {
        return repository.updateUserName(id, name);
    }

    // Direct Thread creation - should trigger THREAD-MGMT-001
    @Async
    public void asyncCallRepo(Long id, String email) {
        repository.updateUserEmail(id, email);
    }

    public void processAsync() {
        new Thread(() -> {
            System.out.println("Processing in new thread");
        }).start();
    }
}
