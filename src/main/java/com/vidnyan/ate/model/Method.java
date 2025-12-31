package com.vidnyan.ate.model;

import lombok.Builder;
import lombok.Value;
import java.util.List;
import java.util.Set;

/**
 * Represents a method in a type.
 * Immutable entity - core of the Source Model.
 */
@Value
@Builder
public class Method {
    String name;
    String signature; // Method signature: name(params)
    String fullyQualifiedName; // Type.fqn + "#" + signature
    TypeRef returnType;
    List<Parameter> parameters;
    Set<Modifier> modifiers;
    List<Annotation> annotations;
    Location location;
    String containingTypeFqn;
    
    // Metadata (computed during model construction)
    boolean isSpringBean;
    boolean isTransactional;
    boolean isScheduled;
    boolean isAutowired;
    int cyclomaticComplexity;
    
    public boolean hasAnnotation(String annotationName) {
        return annotations.stream()
                .anyMatch(a -> a.getName().equals(annotationName) || 
                             a.getFullyQualifiedName().equals(annotationName));
    }
    
    public boolean isPublic() {
        return modifiers.contains(Modifier.PUBLIC);
    }
    
    public boolean isStatic() {
        return modifiers.contains(Modifier.STATIC);
    }
}

