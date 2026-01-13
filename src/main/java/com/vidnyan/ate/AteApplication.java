package com.vidnyan.ate;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * ATE - Agentic Static Analysis Engine
 * 
 * Uses ArchUnit for code parsing and Gen AI for rule evaluation.
 */
@SpringBootApplication
public class AteApplication {

    public static void main(String[] args) {
        SpringApplication.run(AteApplication.class, args);
    }
}
