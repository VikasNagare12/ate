package com.vidnyan.ate.domain.model;

/**
 * Reference to a type (for return types, field types, parameter types, etc.).
 */
public record TypeRef(
    String simpleName,
    String fullyQualifiedName,
    boolean isPrimitive,
    boolean isArray,
    boolean isGeneric,
    java.util.List<TypeRef> typeArguments
) {
    
    /**
     * Create a simple type reference.
     */
    public static TypeRef of(String fqn) {
        String simple = fqn.contains(".") ? fqn.substring(fqn.lastIndexOf('.') + 1) : fqn;
        return new TypeRef(simple, fqn, false, false, false, java.util.List.of());
    }
    
    /**
     * Create a primitive type reference.
     */
    public static TypeRef primitive(String name) {
        return new TypeRef(name, name, true, false, false, java.util.List.of());
    }
    
    /**
     * Check if this type is void.
     */
    public boolean isVoid() {
        return "void".equals(simpleName);
    }
}
