package com.vidnyan.ate.domain.model;

import java.util.List;
import java.util.Set;

/**
 * Represents a type (class, interface, enum, record) in the source model.
 * Immutable value object.
 */
public record TypeEntity(
    String fullyQualifiedName,
    String simpleName,
    String packageName,
    TypeKind kind,
    Set<Modifier> modifiers,
    List<AnnotationRef> annotations,
    List<TypeRef> supertypes,
    List<TypeRef> interfaces,
    Location location
) {
    
    public enum TypeKind {
        CLASS,
        INTERFACE,
        ENUM,
        RECORD,
        ANNOTATION
    }
    
    public enum Modifier {
        PUBLIC, PRIVATE, PROTECTED,
        STATIC, FINAL, ABSTRACT,
        SYNCHRONIZED, VOLATILE, TRANSIENT,
        NATIVE, STRICTFP
    }
    
    /**
     * Check if this type has a specific annotation.
     */
    public boolean hasAnnotation(String annotationName) {
        return annotations.stream()
                .anyMatch(a -> a.simpleName().equals(annotationName) 
                        || a.fullyQualifiedName().equals(annotationName));
    }
    
    /**
     * Check if this type extends or implements a specific type.
     */
    public boolean isSubtypeOf(String typeFqn) {
        return supertypes.stream().anyMatch(t -> t.fullyQualifiedName().equals(typeFqn))
                || interfaces.stream().anyMatch(t -> t.fullyQualifiedName().equals(typeFqn));
    }
}
