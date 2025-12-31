package com.vidnyan.ate.rule;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Value;

import java.util.Map;

/**
 * Rule definition loaded from JSON.
 * Rules are declarative - they define WHAT to find, not HOW to find it.
 */
@Value
@Builder
public class RuleDefinition {
    @JsonProperty("ruleId")
    String ruleId;
    
    @JsonProperty("severity")
    Severity severity;
    
    @JsonProperty("description")
    String description;
    
    @JsonProperty("query")
    QueryDefinition query;
    
    @JsonProperty("violation")
    ViolationDefinition violation;
    
    @Value
    @Builder
    public static class QueryDefinition {
        @JsonProperty("type")
        String type; // "graph_traversal", "model_query", "pattern_match"
        
        @JsonProperty("graph")
        String graph; // "call_graph", "dependency_graph"
        
        @JsonProperty("pattern")
        Map<String, Object> pattern; // Pattern definition (varies by type)
    }
    
    @Value
    @Builder
    public static class ViolationDefinition {
        @JsonProperty("message")
        String message; // Template with {placeholders}
        
        @JsonProperty("location")
        String location; // Which entity to report location from
    }
}

