package com.vidnyan.ate.domain.model;

import java.util.Map;

/**
 * Represents a relationship between two code elements.
 * Immutable value object with rich metadata.
 */
public record Relationship(
    RelationshipType type,
    String sourceEntityFqn,
    String targetEntityFqn,
    String resolvedTargetFqn,
    Location location,
    Map<String, Object> metadata
) {
    
    /**
     * Types of relationships between code elements.
     */
    public enum RelationshipType {
        CALLS,              // Method calls another method
        EXTENDS,            // Type extends another type
        IMPLEMENTS,         // Type implements an interface
        USES_TYPE,          // Method/field uses a type
        USES_FIELD,         // Method accesses a field
        ANNOTATED_WITH,     // Element has annotation
        THROWS,             // Method declares thrown exception
        CONTAINS            // Type contains method/field
    }
    
    /**
     * Get the best available target FQN (resolved if available).
     */
    public String effectiveTargetFqn() {
        return resolvedTargetFqn != null ? resolvedTargetFqn : targetEntityFqn;
    }
    
    /**
     * Get metadata value.
     */
    @SuppressWarnings("unchecked")
    public <T> T getMetadata(String key, Class<T> type) {
        return (T) metadata.get(key);
    }
    
    /**
     * Check if this is a method call relationship.
     */
    public boolean isCall() {
        return type == RelationshipType.CALLS;
    }
    
    /**
     * Builder for creating relationships.
     */
    public static Builder builder() {
        return new Builder();
    }
    
    public static class Builder {
        private RelationshipType type;
        private String sourceEntityFqn;
        private String targetEntityFqn;
        private String resolvedTargetFqn;
        private Location location;
        private Map<String, Object> metadata = Map.of();
        
        public Builder type(RelationshipType type) { this.type = type; return this; }
        public Builder source(String fqn) { this.sourceEntityFqn = fqn; return this; }
        public Builder target(String fqn) { this.targetEntityFqn = fqn; return this; }
        public Builder resolvedTarget(String fqn) { this.resolvedTargetFqn = fqn; return this; }
        public Builder location(Location loc) { this.location = loc; return this; }
        public Builder metadata(Map<String, Object> meta) { this.metadata = meta; return this; }
        
        public Relationship build() {
            return new Relationship(type, sourceEntityFqn, targetEntityFqn, 
                    resolvedTargetFqn, location, metadata);
        }
    }
}
