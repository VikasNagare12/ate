package com.vidnyan.ate.examples;

/**
 * Example: THREAD-MGMT-001 Violation
 * Direct thread creation instead of using ExecutorService.
 */
public class ThreadManagementViolation {

    // VIOLATION: Creating threads directly
    public void processAsync(String data) {
        Thread thread = new Thread(() -> {
            processData(data);
        });
        thread.start();
    }
    
    // VIOLATION: Anonymous thread creation
    public void runBackgroundTask() {
        new Thread() {
            @Override
            public void run() {
                doBackgroundWork();
            }
        }.start();
    }
    
    private void processData(String data) {
        System.out.println("Processing: " + data);
    }
    
    private void doBackgroundWork() {
        System.out.println("Background work");
    }
}
