package com.vidnyan.ate;

import jakarta.annotation.PostConstruct;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Configuration properties for the analysis engine.
 * Can be configured via application.properties or application.yml
 */
@Data
@Component
@ConfigurationProperties(prefix = "ate.analysis")
public class AnalysisProperties {
    
    /**
     * Path to the repository to analyze.
     * Default: current directory
     */
    private String repositoryPath = ".";
    
    /**
     * List of rule files to load.
     * Default: rules from resources
     */
    private List<String> ruleFiles = new ArrayList<>();
    
    @PostConstruct
    public void init() {
        // Set default rule files if not configured
        if (ruleFiles.isEmpty()) {
            ruleFiles.add("src/main/resources/rules/scheduled-job-resiliency.json");
            ruleFiles.add("src/main/resources/rules/transaction-boundary-violation.json");
            ruleFiles.add("src/main/resources/rules/circular-dependency.json");
        }
    }
}

