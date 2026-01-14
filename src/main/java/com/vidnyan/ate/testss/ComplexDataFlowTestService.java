package com.vidnyan.ate.testss;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ComplexDataFlowTestService {

    private final DynamicExecutorService executor;
    private final JdbcExampleRepository repository;

    public ComplexDataFlowTestService(DynamicExecutorService executor, JdbcExampleRepository repository) {
        this.executor = executor;
        this.repository = repository;
    }

    @Transactional
    public void complexChainViolation() {
        // Source of query is a constant
        String sql = QueryConstants.UPDATE_USERS;
        //String sql1 = "UPDATE users1 SET status = 'ACTIVE'";

        // Update 1: Passed to another class
        executor.executeSql(sql);

        // Update 2: Passed to another class
        executor.executeSql(sql);
    }

    //@Transactional
    public void complexChainViolatio11n() {
        // Source of query is a constant
        String sql = QueryConstants.UPDATE_USERS;
        String sql1 = "INSERT INTO users1 SET status = 'ACTIVE'";

        // Update 1: Passed to another class
        executor.executeSql(sql, new Object());

        // Update 2: Passed to another class
        executor.executeSql(sql1, new Object[]{new Object()});
    }

//    @Transactional
//    public void simpleViolation() {
//        // This SHOULD be detected because the called method has the SQL string literal
//        // directly
//        repository.updateUserEmail(1L, "test1@example.com");
//        repository.updateUserEmail(1L, "test2@example.com");
//    }
}
