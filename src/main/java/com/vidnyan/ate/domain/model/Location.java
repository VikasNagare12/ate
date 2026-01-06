package com.vidnyan.ate.domain.model;

/**
 * Source code location.
 */
public record Location(
    String filePath,
    int line,
    int column,
    int endLine,
    int endColumn
) {
    
    /**
     * Create a location with just line information.
     */
    public static Location at(String filePath, int line, int column) {
        return new Location(filePath, line, column, line, column);
    }
    
    /**
     * Format as readable string.
     */
    public String format() {
        return filePath + ":" + line + ":" + column;
    }
}
