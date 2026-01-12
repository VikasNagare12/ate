package com.vidnyan.ate.testss;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class DistributedTransactionTestService {

    private final JdbcExampleRepository repository;
    private final JdbcExampleService service;

    public DistributedTransactionTestService(JdbcExampleRepository repository, JdbcExampleService service) {
        this.repository = repository;
        this.service = service;
    }

    @Transactional
    public void distributedUpdateViolation() {
        // Update 1: Direct call to repository
        repository.updateUserEmail(1L, "newemail@test.com");

        // Update 2: Indirect call via another service, which eventually calls the
        // repository again (or runs another update)
        // JdbcExampleService.updateUser calls JdbcExampleRepository.updateUserName
        service.updateUser(1L, "New Name");
    }
}
