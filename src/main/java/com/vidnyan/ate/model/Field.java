package com.vidnyan.ate.model;

import lombok.Builder;
import lombok.Value;

import java.util.List;
import java.util.Set;

/**
 * Represents a field in a type.
 * Immutable entity.
 */
@Value
@Builder
public class Field {
    String name;
    String fullyQualifiedName; // Type.fqn + "." + name
    TypeRef type;
    Set<Modifier> modifiers;
    List<Annotation> annotations;
    Location location;
    String containingTypeFqn;
    
    public boolean hasAnnotation(String annotationName) {
        return annotations.stream()
                .anyMatch(a -> a.getName().equals(annotationName) || 
                             a.getFullyQualifiedName().equals(annotationName));
    }
}

