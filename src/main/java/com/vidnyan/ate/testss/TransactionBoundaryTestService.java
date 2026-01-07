package com.vidnyan.ate.testss;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class TransactionBoundaryTestService {

    private final RemoteService remoteService;

    // Direct Violations

    @Transactional
    public void directRestTemplateViolation() {
        remoteService.callViaRestTemplate();
    }

    @Transactional
    public void directWebClientViolation() {
        remoteService.callViaWebClient();
    }

    @Transactional
    public void directHttpClientViolation() throws Exception {
        remoteService.callViaHttpClient();
    }

    // Indirect Violation (Chain)

    @Transactional
    public void indirectViolation() {
        innerServiceMethod();
    }

    private void innerServiceMethod() {
        remoteService.callViaRestTemplate();
    }

    // Deep Chain Violation

    @Transactional
    public void deepChainViolation() {
        level1();
    }

    private void level1() {
        level2();
    }

    private void level2() {
        remoteService.callViaRestTemplate();
    }

    // Safe Scenarios

    public void safeRemoteCallOutsideTx() {
        remoteService.callViaRestTemplate();
    }

    @Transactional
    public void safeTransactionalMethod() {
        // Just database operations (implicit in name)
        System.out.println("Doing DB work safely...");
    }

    // REMOTE-RETRY-001 Violations

    @org.springframework.retry.annotation.Retryable
    public void retryOnRestTemplate() {
        remoteService.callViaRestTemplate();
    }

    @org.springframework.retry.annotation.Retryable
    public void retryOnKafka() {
        remoteService.sendKafkaMessage();
    }

    @org.springframework.retry.annotation.Retryable
    public void indirectRetryViolation() {
        remoteService.callViaWebClient();
    }
}
