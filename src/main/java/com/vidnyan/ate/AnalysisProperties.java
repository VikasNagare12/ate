package com.vidnyan.ate;


import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;



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
     * Directory containing rule files.
     * Default: "rules" (relative to classpath or file system)
     */
    private String ruleDirectory = "rules";
}

