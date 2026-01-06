package com.vidnyan.ate.model;

import lombok.Builder;
import lombok.Value;

/**
 * Represents a relationship between two entities in the Source Model.
 * Immutable entity.
 */
@Value
@Builder
public class Relationship {
    RelationshipType type;
    String sourceEntityId; // FQN or method signature
    String targetEntityId; // Raw signature from AST (e.g. "jdbcTemplate.query(...)")
    String resolvedTargetEntityId; // Resolved FQN (e.g. "org.springframework.jdbc.core.JdbcTemplate#query(...)")
    Location location; // Where the relationship occurs in source
    CallType callType; // For CALLS relationships
    
    public enum CallType {
        DIRECT,      // Direct method call
        VIRTUAL,     // Virtual method call (polymorphism)
        INTERFACE,   // Interface method call
        REFLECTION,  // Reflection-based call
        LAMBDA       // Lambda/method reference
    }
}

