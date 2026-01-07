package com.vidnyan.ate.testss;

import org.springframework.retry.annotation.Retryable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller for testing JDBC retry path analysis.
 */
@RestController
public class TestController {

    private final JdbcExampleService service;

    public TestController(JdbcExampleService service) {
        this.service = service;
    }

    /**
     * UNPROTECTED PATH:
     * TestController.unsafePath() -> JdbcExampleService.createUser() ->
     * JdbcExampleRepository.createUser() -> JdbcTemplate.update()
     * No @Retryable in this chain.
     */
    @GetMapping("/test/unsafe")
    public void unsafePath() {
        service.createUser("test", "test@example.com");
    }

    /**
     * PROTECTED PATH:
     * TestController.safePath() -> JdbcExampleService.getUserName() ->
     * JdbcExampleRepository.findUserById() -> JdbcTemplate.queryForObject()
     * JdbcExampleService.getUserName() has @Retryable.
     */
    @GetMapping("/async-safety-test")
    @Retryable
    public String asyncSafetyTest(@RequestParam Long id, @RequestParam String email) {
        // This retry is INVALID for the database call inside asyncCallRepo
        service.asyncCallRepo(id, email);
        return "Async call triggered with ineffective retry";
    }

    @GetMapping("/safe-path")
    public void safePath() {
        service.getUserName(1L);
    }
}
