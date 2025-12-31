package com.vidnyan.ate.model;

import lombok.Builder;
import lombok.Value;
import java.util.List;
import java.util.Set;

/**
 * Represents a Java type (class, interface, enum, annotation).
 * Immutable entity - core of the Source Model.
 */
@Value
@Builder
public class Type {
    String simpleName;
    String fullyQualifiedName;
    String packageName;
    TypeKind kind;
    Set<Modifier> modifiers;
    List<Annotation> annotations;
    List<TypeRef> superTypes; // extends and implements
    List<Method> methods;
    List<Field> fields;
    Location location;
    
    // Metadata (computed during model construction)
    boolean isSpringComponent;
    boolean isSpringConfiguration;
    
    public boolean hasAnnotation(String annotationName) {
        return annotations.stream()
                .anyMatch(a -> a.getName().equals(annotationName) || 
                             a.getFullyQualifiedName().equals(annotationName));
    }
    
    public boolean isPublic() {
        return modifiers.contains(Modifier.PUBLIC);
    }
    
    public List<Method> getMethodsWithAnnotation(String annotationName) {
        return methods.stream()
                .filter(m -> m.hasAnnotation(annotationName))
                .toList();
    }
}

