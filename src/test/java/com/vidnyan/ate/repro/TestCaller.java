package com.vidnyan.ate.repro;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TestCaller {
    
    private final TestInterface testInterface;

    public TestCaller(TestInterface testInterface) {
        this.testInterface = testInterface;
    }

    @Transactional
    public void doTransaction() {
        testInterface.remoteCall();
    }
}
