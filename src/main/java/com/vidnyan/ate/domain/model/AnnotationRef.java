package com.vidnyan.ate.domain.model;

/**
 * Reference to an annotation on a code element.
 */
public record AnnotationRef(
    String simpleName,
    String fullyQualifiedName,
    java.util.Map<String, Object> attributes
) {
    
    /**
     * Check if annotation has a specific attribute.
     */
    public boolean hasAttribute(String name) {
        return attributes.containsKey(name);
    }
    
    /**
     * Get attribute value.
     */
    @SuppressWarnings("unchecked")
    public <T> T getAttribute(String name, Class<T> type) {
        return (T) attributes.get(name);
    }
}
