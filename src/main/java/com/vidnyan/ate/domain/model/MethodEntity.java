package com.vidnyan.ate.domain.model;

import java.util.List;
import java.util.Set;

/**
 * Represents a method in the source model.
 * Immutable value object.
 */
public record MethodEntity(
    String fullyQualifiedName,
    String simpleName,
    String containingTypeFqn,
    TypeRef returnType,
    List<Parameter> parameters,
    Set<TypeEntity.Modifier> modifiers,
    List<AnnotationRef> annotations,
    List<TypeRef> thrownExceptions,
    Location location
) {
    
    /**
     * Method parameter.
     */
    public record Parameter(
        String name,
        TypeRef type,
        List<AnnotationRef> annotations
    ) {}
    
    /**
     * Check if method has a specific annotation.
     */
    public boolean hasAnnotation(String annotationName) {
        return annotations.stream()
                .anyMatch(a -> a.simpleName().equals(annotationName)
                        || a.fullyQualifiedName().equals(annotationName));
    }
    
    /**
     * Get annotation by name.
     */
    public java.util.Optional<AnnotationRef> getAnnotation(String annotationName) {
        return annotations.stream()
                .filter(a -> a.simpleName().equals(annotationName)
                        || a.fullyQualifiedName().equals(annotationName))
                .findFirst();
    }
    
    /**
     * Check if method is public.
     */
    public boolean isPublic() {
        return modifiers.contains(TypeEntity.Modifier.PUBLIC);
    }
    
    /**
     * Check if method is static.
     */
    public boolean isStatic() {
        return modifiers.contains(TypeEntity.Modifier.STATIC);
    }
    
    /**
     * Build signature string (e.g., "createUser(User, String)").
     */
    public String signature() {
        String params = parameters.stream()
                .map(p -> p.type().simpleName())
                .reduce((a, b) -> a + ", " + b)
                .orElse("");
        return simpleName + "(" + params + ")";
    }
}
