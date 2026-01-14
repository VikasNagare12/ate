package com.vidnyan.ate.domain.model;

/**
 * Location in source code.
 */
public record Location(String file, int line, int column) {
    public static Location at(String file, int line, int column) {
        return new Location(file, line, column);
    }

    public String format() {
        return file + ":" + line;
    }
}
