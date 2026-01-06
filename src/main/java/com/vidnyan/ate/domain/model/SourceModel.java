package com.vidnyan.ate.domain.model;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * The complete source model - a queryable representation of the analyzed codebase.
 * Immutable aggregate root.
 */
public record SourceModel(
    Map<String, TypeEntity> types,
    Map<String, MethodEntity> methods,
    Map<String, FieldEntity> fields,
    List<Relationship> relationships
) {
    
    /**
     * Get type by FQN.
     */
    public Optional<TypeEntity> getType(String fqn) {
        return Optional.ofNullable(types.get(fqn));
    }
    
    /**
     * Get method by FQN.
     */
    public Optional<MethodEntity> getMethod(String fqn) {
        return Optional.ofNullable(methods.get(fqn));
    }
    
    /**
     * Get field by FQN.
     */
    public Optional<FieldEntity> getField(String fqn) {
        return Optional.ofNullable(fields.get(fqn));
    }
    
    /**
     * Get all methods in a type.
     */
    public List<MethodEntity> getMethodsInType(String typeFqn) {
        return methods.values().stream()
                .filter(m -> m.containingTypeFqn().equals(typeFqn))
                .toList();
    }
    
    /**
     * Get all fields in a type.
     */
    public List<FieldEntity> getFieldsInType(String typeFqn) {
        return fields.values().stream()
                .filter(f -> f.containingTypeFqn().equals(typeFqn))
                .toList();
    }
    
    /**
     * Get relationships of a specific type.
     */
    public List<Relationship> getRelationships(Relationship.RelationshipType type) {
        return relationships.stream()
                .filter(r -> r.type() == type)
                .toList();
    }
    
    /**
     * Get relationships from a specific source.
     */
    public List<Relationship> getRelationshipsFrom(String sourceFqn) {
        return relationships.stream()
                .filter(r -> r.sourceEntityFqn().equals(sourceFqn))
                .toList();
    }
    
    /**
     * Get relationships to a specific target.
     */
    public List<Relationship> getRelationshipsTo(String targetFqn) {
        return relationships.stream()
                .filter(r -> r.targetEntityFqn().equals(targetFqn) 
                        || targetFqn.equals(r.resolvedTargetFqn()))
                .toList();
    }
    
    /**
     * Find all methods with a specific annotation.
     */
    public List<MethodEntity> findMethodsWithAnnotation(String annotationName) {
        return methods.values().stream()
                .filter(m -> m.hasAnnotation(annotationName))
                .toList();
    }
    
    /**
     * Find all types with a specific annotation.
     */
    public List<TypeEntity> findTypesWithAnnotation(String annotationName) {
        return types.values().stream()
                .filter(t -> t.hasAnnotation(annotationName))
                .toList();
    }
    
    /**
     * Get statistics.
     */
    public Stats stats() {
        return new Stats(
                types.size(),
                methods.size(),
                fields.size(),
                relationships.size(),
                relationships.stream()
                        .filter(r -> r.type() == Relationship.RelationshipType.CALLS)
                        .count()
        );
    }
    
    public record Stats(
        int typeCount,
        int methodCount,
        int fieldCount,
        int relationshipCount,
        long callCount
    ) {}
    
    /**
     * Builder for constructing SourceModel.
     */
    public static Builder builder() {
        return new Builder();
    }
    
    public static class Builder {
        private Map<String, TypeEntity> types = Map.of();
        private Map<String, MethodEntity> methods = Map.of();
        private Map<String, FieldEntity> fields = Map.of();
        private List<Relationship> relationships = List.of();
        
        public Builder types(Map<String, TypeEntity> types) { this.types = types; return this; }
        public Builder methods(Map<String, MethodEntity> methods) { this.methods = methods; return this; }
        public Builder fields(Map<String, FieldEntity> fields) { this.fields = fields; return this; }
        public Builder relationships(List<Relationship> rels) { this.relationships = rels; return this; }
        
        public SourceModel build() {
            return new SourceModel(types, methods, fields, relationships);
        }
    }
}
