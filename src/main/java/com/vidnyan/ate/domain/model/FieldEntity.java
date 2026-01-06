package com.vidnyan.ate.domain.model;

import java.util.List;
import java.util.Set;

/**
 * Represents a field in the source model.
 * Immutable value object.
 */
public record FieldEntity(
    String fullyQualifiedName,
    String simpleName,
    String containingTypeFqn,
    TypeRef type,
    Set<TypeEntity.Modifier> modifiers,
    List<AnnotationRef> annotations,
    Location location
) {
    
    /**
     * Check if field has a specific annotation.
     */
    public boolean hasAnnotation(String annotationName) {
        return annotations.stream()
                .anyMatch(a -> a.simpleName().equals(annotationName)
                        || a.fullyQualifiedName().equals(annotationName));
    }
    
    /**
     * Check if field is final.
     */
    public boolean isFinal() {
        return modifiers.contains(TypeEntity.Modifier.FINAL);
    }
    
    /**
     * Check if field is static.
     */
    public boolean isStatic() {
        return modifiers.contains(TypeEntity.Modifier.STATIC);
    }
}
