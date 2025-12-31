package com.vidnyan.ate.model;

/**
 * Types of relationships between entities in the Source Model.
 */
public enum RelationshipType {
    CALLS,           // Method calls another method
    REFERENCES,      // Type references another type
    INHERITS,        // Type extends/implements another type
    ANNOTATES,       // Annotation applied to element
    ACCESSES,        // Method accesses a field
    INJECTS,         // Dependency injection relationship
    CONTAINS         // Type contains method/field
}

