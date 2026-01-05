package com.vidnyan.ate.model;

import lombok.Builder;
import lombok.Value;

import java.util.Map;

/**
 * Represents a Java annotation with its values.
 * Immutable value object.
 */
@Value
@Builder
public class Annotation {
    String name; // Simple name, e.g., "Transactional"
    String fullyQualifiedName; // e.g., "org.springframework.transaction.annotation.Transactional"
    Map<String, Object> values; // Annotation attribute values
    Location location;
    
    public boolean hasValue(String key) {
        return values != null && values.containsKey(key);
    }
    
    public Object getValue(String key) {
        return values != null ? values.get(key) : null;
    }
}

