package com.vidnyan.ate.rule;

/**
 * Violation severity levels.
 */
public enum Severity {
    BLOCKER,  // Must fix - blocks CI
    ERROR,    // Should fix - fails CI if threshold exceeded
    WARN,     // Should review - passes CI but reported
    INFO      // Informational only
}

