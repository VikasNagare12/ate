package com.vidnyan.ate.domain.rule;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Rule definition - describes what to check and how to report.
 * Immutable value object loaded from JSON/YAML.
 */
public record RuleDefinition(
    String id,
    String name,
    String description,
    Severity severity,
    Category category,
    Detection detection,
    Remediation remediation,
    Map<String, Object> config
) {
    
    public enum Severity {
        BLOCKER,    // Must fix before deployment
        ERROR,      // Should fix before deployment
        WARN,       // Should fix but not blocking
        INFO        // Informational, best practice suggestions
    }
    
    public enum Category {
        TRANSACTION_SAFETY,
        ASYNC_SAFETY,
        RETRY_SAFETY,
        CIRCULAR_DEPENDENCY,
        LAYERED_ARCHITECTURE,
        SECURITY,
        PERFORMANCE,
        CUSTOM
    }
    
    /**
     * Detection configuration - what patterns to look for.
     */
    public record Detection(
        EntryPoints entryPoints,
        Sinks sinks,
        PathConstraints pathConstraints
    ) {
        
        public record EntryPoints(
            List<String> annotations,
            List<String> types,
            List<String> methodPatterns
        ) {}
        
        public record Sinks(
            List<String> annotations,
            List<String> types,
            List<String> methodPatterns
        ) {}
        
        public record PathConstraints(
            List<String> mustContain,
            List<String> mustNotContain,
            int maxDepth
        ) {}
    }
    
    /**
     * Remediation guidance.
     */
    public record Remediation(
        String quickFix,
        String explanation,
        List<String> references
    ) {}
    
    /**
     * Get config value.
     */
    @SuppressWarnings("unchecked")
    public <T> Optional<T> getConfig(String key, Class<T> type) {
        return Optional.ofNullable((T) config.get(key));
    }
    
    /**
     * Builder for RuleDefinition.
     */
    public static Builder builder() {
        return new Builder();
    }
    
    public static class Builder {
        private String id;
        private String name;
        private String description;
        private Severity severity = Severity.ERROR;
        private Category category = Category.CUSTOM;
        private Detection detection;
        private Remediation remediation;
        private Map<String, Object> config = Map.of();
        
        public Builder id(String id) { this.id = id; return this; }
        public Builder name(String name) { this.name = name; return this; }
        public Builder description(String desc) { this.description = desc; return this; }
        public Builder severity(Severity sev) { this.severity = sev; return this; }
        public Builder category(Category cat) { this.category = cat; return this; }
        public Builder detection(Detection det) { this.detection = det; return this; }
        public Builder remediation(Remediation rem) { this.remediation = rem; return this; }
        public Builder config(Map<String, Object> cfg) { this.config = cfg; return this; }
        
        public RuleDefinition build() {
            return new RuleDefinition(id, name, description, severity, category, 
                    detection, remediation, config);
        }
    }
}
