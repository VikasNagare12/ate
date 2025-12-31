package com.vidnyan.ate.model;

import lombok.Builder;
import lombok.Value;

/**
 * Reference to a type (resolved or unresolved).
 * Immutable value object.
 */
@Value
@Builder
public class TypeRef {
    String fullyQualifiedName;
    boolean isPrimitive;
    boolean isArray;
    boolean isGeneric;
    String genericSignature; // e.g., "List<String>"
    
    public static TypeRef of(String fqn) {
        return TypeRef.builder()
                .fullyQualifiedName(fqn)
                .isPrimitive(false)
                .isArray(false)
                .isGeneric(false)
                .build();
    }
}

