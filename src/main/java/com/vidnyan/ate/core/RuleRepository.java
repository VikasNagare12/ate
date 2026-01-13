package com.vidnyan.ate.core;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Repository for loading architectural rules from JSON files.
 */
@Component
public class RuleRepository {

    private static final Logger log = LoggerFactory.getLogger(RuleRepository.class);
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Load all rules from the rules directory.
     */
    public List<Rule> loadAllRules() {
        List<Rule> rules = new ArrayList<>();
        
        try {
            PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
            Resource[] resources = resolver.getResources("classpath:rules/*.json");
            
            for (Resource resource : resources) {
                try {
                    Rule rule = objectMapper.readValue(resource.getInputStream(), Rule.class);
                    rules.add(rule);
                    log.debug("Loaded rule: {}", rule.id());
                } catch (IOException e) {
                    log.warn("Failed to load rule from: {}", resource.getFilename(), e);
                }
            }
        } catch (IOException e) {
            log.error("Failed to scan for rules", e);
        }
        
        return rules;
    }

    /**
     * Load a specific rule by ID.
     */
    public Rule loadRule(String ruleId) {
        String path = "rules/" + ruleId.toLowerCase() + ".json";
        try {
            ClassPathResource resource = new ClassPathResource(path);
            return objectMapper.readValue(resource.getInputStream(), Rule.class);
        } catch (IOException e) {
            log.error("Failed to load rule: {}", ruleId, e);
            return null;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Rule(
        String id,
        String name,
        String description,
        String severity,
        RuleConfig config
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record RuleConfig(
        List<String> sinkPatterns,
        List<String> sourcePatterns,
        String annotationRequired
    ) {}
}
