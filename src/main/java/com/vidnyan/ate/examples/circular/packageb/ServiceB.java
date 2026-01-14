package com.vidnyan.ate.examples.circular.packageb;

import com.vidnyan.ate.examples.circular.ServiceA;

/**
 * Example: CIRCULAR-DEP-001 Violation
 * This creates circular dependency: packageb.ServiceB -> circular.ServiceA
 */
public class ServiceB {

    private final ServiceA serviceA;

    public ServiceB(ServiceA serviceA) {
        this.serviceA = serviceA;
    }

    public void callBack() {
        // VIOLATION: Calling back to parent package
        serviceA.callback();
    }
}
