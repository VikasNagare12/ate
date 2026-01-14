package com.vidnyan.ate.examples.circular;

import com.vidnyan.ate.examples.circular.packageb.ServiceB;

/**
 * Example: CIRCULAR-DEP-001 Violation
 * Circular dependency between packages.
 * 
 * circular.ServiceA -> circular.packageb.ServiceB -> circular.ServiceA
 */
public class ServiceA {

    private final ServiceB serviceB;

    public ServiceA(ServiceB serviceB) {
        this.serviceB = serviceB;
    }

    public void doSomething() {
        serviceB.callBack();
    }
    
    public void callback() {
        System.out.println("ServiceA callback");
    }
}
