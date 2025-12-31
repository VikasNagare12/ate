package com.vidnyan.ate.model;

import lombok.Builder;
import lombok.Value;

/**
 * Represents a method parameter.
 * Immutable value object.
 */
@Value
@Builder
public class Parameter {
    String name;
    TypeRef type;
    boolean isVarArgs;
    Location location;
}

