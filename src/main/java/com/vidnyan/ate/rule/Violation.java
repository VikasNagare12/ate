package com.vidnyan.ate.rule;

import com.vidnyan.ate.model.Location;
import lombok.Builder;
import lombok.Value;

import java.util.Map;

/**
 * Represents a rule violation.
 * Immutable value object.
 */
@Value
@Builder
public class Violation {
    String ruleId;
    Severity severity;
    String message;
    Location location;
    Map<String, Object> context; // Additional context (method names, paths, etc.)
    String fingerprint; // Hash for deduplication
    
    /**
     * Generate fingerprint for deduplication.
     */
    public static String generateFingerprint(String ruleId, Location location, Map<String, Object> context) {
        // Simple hash - in production, use proper hashing
        return String.format("%s:%s:%s", ruleId, location.toDisplayString(), context.hashCode());
    }
}

