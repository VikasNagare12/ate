package com.vidnyan.ate.model;

import lombok.Builder;
import lombok.Value;

import java.util.List;
import java.util.Map;

/**
 * The canonical Source Model - the central immutable data structure.
 * This is the single source of truth for all static analysis.
 * 
 * Properties:
 * - Immutable after construction
 * - Fully resolved (all symbols resolved)
 * - Indexed for O(1) lookups
 * - Self-contained (no external dependencies)
 */
@Value
@Builder
public class SourceModel {
    // Core entities
    Map<String, Type> types; // FQN -> Type
    Map<String, Method> methods; // Method signature -> Method
    Map<String, Field> fields; // Field signature -> Field
    Map<String, Annotation> annotations; // Annotation key -> Annotation
    
    // Relationships
    List<Relationship> relationships;
    
    // Indexes for fast lookups
    Map<String, List<Type>> typesByPackage; // Package -> Types
    Map<String, List<Method>> methodsByAnnotation; // Annotation -> Methods
    Map<String, List<Type>> typesByAnnotation; // Annotation -> Types
    Map<String, List<Field>> fieldsByAnnotation; // Annotation -> Fields
    
    // Reverse indexes
    Map<String, List<Relationship>> relationshipsBySource; // Entity -> Outgoing relationships
    Map<String, List<Relationship>> relationshipsByTarget; // Entity -> Incoming relationships
    
    boolean isFrozen; // Model is immutable after freezing
    
    /**
     * Get a type by fully qualified name.
     */
    public Type getType(String fqn) {
        return types.get(fqn);
    }
    
    /**
     * Get a method by signature.
     */
    public Method getMethod(String signature) {
        return methods.get(signature);
    }
    
    /**
     * Get all types in a package.
     */
    public List<Type> getTypesInPackage(String packageName) {
        return typesByPackage.getOrDefault(packageName, List.of());
    }
    
    /**
     * Get all methods annotated with a specific annotation.
     */
    public List<Method> getMethodsAnnotatedWith(String annotationName) {
        return methodsByAnnotation.getOrDefault(annotationName, List.of());
    }
    
    /**
     * Get all types annotated with a specific annotation.
     */
    public List<Type> getTypesAnnotatedWith(String annotationName) {
        return typesByAnnotation.getOrDefault(annotationName, List.of());
    }
    
    /**
     * Get all outgoing relationships from an entity.
     */
    public List<Relationship> getOutgoingRelationships(String entityId) {
        return relationshipsBySource.getOrDefault(entityId, List.of());
    }
    
    /**
     * Get all incoming relationships to an entity.
     */
    public List<Relationship> getIncomingRelationships(String entityId) {
        return relationshipsByTarget.getOrDefault(entityId, List.of());
    }
    
    /**
     * Get all relationships of a specific type.
     */
    public List<Relationship> getRelationships(RelationshipType type) {
        return relationships.stream()
                .filter(r -> r.getType() == type)
                .toList();
    }
    
    /**
     * Validate that the model is complete and consistent.
     */
    public void validate() {
        if (!isFrozen) {
            throw new IllegalStateException("Model must be frozen before validation");
        }
        // Add validation logic here
    }
}

