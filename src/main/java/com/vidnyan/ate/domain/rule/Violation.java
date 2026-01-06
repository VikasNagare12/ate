package com.vidnyan.ate.domain.rule;

import com.vidnyan.ate.domain.model.Location;

import java.util.List;
import java.util.Map;

/**
 * A detected rule violation.
 * Immutable value object.
 */
public record Violation(
    String ruleId,
    String ruleName,
    RuleDefinition.Severity severity,
    String message,
    Location location,
    List<String> callChain,
    Map<String, Object> context
) {
    
    /**
     * Format call chain for display.
     */
    public String formattedCallChain() {
        if (callChain == null || callChain.isEmpty()) {
            return "";
        }
        return String.join(" → ", callChain);
    }
    
    /**
     * Get context value.
     */
    @SuppressWarnings("unchecked")
    public <T> T getContext(String key, Class<T> type) {
        return (T) context.get(key);
    }
    
    /**
     * Builder for Violation.
     */
    public static Builder builder() {
        return new Builder();
    }
    
    public static class Builder {
        private String ruleId;
        private String ruleName;
        private RuleDefinition.Severity severity = RuleDefinition.Severity.ERROR;
        private String message;
        private Location location;
        private List<String> callChain = List.of();
        private Map<String, Object> context = Map.of();
        
        public Builder ruleId(String id) { this.ruleId = id; return this; }
        public Builder ruleName(String name) { this.ruleName = name; return this; }
        public Builder severity(RuleDefinition.Severity sev) { this.severity = sev; return this; }
        public Builder message(String msg) { this.message = msg; return this; }
        public Builder location(Location loc) { this.location = loc; return this; }
        public Builder callChain(List<String> chain) { this.callChain = chain; return this; }
        public Builder context(Map<String, Object> ctx) { this.context = ctx; return this; }
        
        public Violation build() {
            return new Violation(ruleId, ruleName, severity, message, location, callChain, context);
        }
    }
}
