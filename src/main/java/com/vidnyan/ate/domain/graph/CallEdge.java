package com.vidnyan.ate.domain.graph;

import com.vidnyan.ate.domain.model.Location;

/**
 * Represents an edge in the call graph.
 * Immutable value object with rich metadata.
 */
public record CallEdge(
    String callerFqn,
    String calleeFqn,
    String resolvedCalleeFqn,
    CallType callType,
    Location location
) {
    
    /**
     * Types of method calls.
     */
    public enum CallType {
        DIRECT,         // Direct method call on same class
        VIRTUAL,        // Virtual method call (polymorphism)
        STATIC,         // Static method call
        CONSTRUCTOR,    // Constructor invocation
        INTERFACE,      // Interface method call
        SUPER,          // super.method() call
        LAMBDA,         // Lambda expression
        METHOD_REF      // Method reference (::)
    }
    
    /**
     * Get the best available callee FQN.
     */
    public String effectiveCalleeFqn() {
        return resolvedCalleeFqn != null ? resolvedCalleeFqn : calleeFqn;
    }
    
    /**
     * Check if callee starts with a specific prefix (for pattern matching).
     */
    public boolean calleeMatches(String prefix) {
        return effectiveCalleeFqn().startsWith(prefix);
    }
    
    /**
     * Builder for CallEdge.
     */
    public static Builder builder() {
        return new Builder();
    }
    
    public static class Builder {
        private String callerFqn;
        private String calleeFqn;
        private String resolvedCalleeFqn;
        private CallType callType = CallType.VIRTUAL;
        private Location location;
        
        public Builder caller(String fqn) { this.callerFqn = fqn; return this; }
        public Builder callee(String fqn) { this.calleeFqn = fqn; return this; }
        public Builder resolvedCallee(String fqn) { this.resolvedCalleeFqn = fqn; return this; }
        public Builder callType(CallType type) { this.callType = type; return this; }
        public Builder location(Location loc) { this.location = loc; return this; }
        
        public CallEdge build() {
            return new CallEdge(callerFqn, calleeFqn, resolvedCalleeFqn, callType, location);
        }
    }
}
