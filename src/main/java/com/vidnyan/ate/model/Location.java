package com.vidnyan.ate.model;

import lombok.Builder;
import lombok.Value;

/**
 * Represents a source code location (file, line, column).
 * Immutable value object.
 */
@Value
@Builder
public class Location {
    String filePath;
    int line;
    int column;
    
    public String toDisplayString() {
        return String.format("%s:%d:%d", filePath, line, column);
    }
}

