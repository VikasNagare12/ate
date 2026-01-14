package com.vidnyan.ate.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.vidnyan.ate.domain.rule.RuleEvaluator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.util.List;

/**
 * Spring configuration for ATE components.
 * Wires together the clean architecture components.
 */
@Slf4j
@Configuration
public class AteConfiguration {

    /**
     * ObjectMapper for JSON parsing.
     */
    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                .configure(SerializationFeature.INDENT_OUTPUT, true);
    }

    /**
     * Log available evaluators on startup.
     */
    @Bean
    public String logEvaluators(List<RuleEvaluator> evaluators) {
        log.info("Registered {} rule evaluators:", evaluators.size());
        evaluators.forEach(e -> log.info("  - {}", e.getName()));
        return "evaluators-logged";
    }
}
