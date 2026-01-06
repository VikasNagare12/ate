package com.vidnyan.ate.adapter.out.parser;

import com.vidnyan.ate.application.port.out.TypeResolver;
import com.vidnyan.ate.domain.model.FieldEntity;
import com.vidnyan.ate.domain.model.TypeRef;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;

/**
 * Implementation of TypeResolver using context-based resolution.
 */
@Slf4j
@Component
public class TypeResolverImpl implements TypeResolver {
    
    private static final Map<String, String> COMMON_JDK_TYPES = Map.ofEntries(
            Map.entry("List", "java.util.List"),
            Map.entry("Set", "java.util.Set"),
            Map.entry("Map", "java.util.Map"),
            Map.entry("ArrayList", "java.util.ArrayList"),
            Map.entry("HashMap", "java.util.HashMap"),
            Map.entry("HashSet", "java.util.HashSet"),
            Map.entry("Arrays", "java.util.Arrays"),
            Map.entry("Collections", "java.util.Collections"),
            Map.entry("Optional", "java.util.Optional"),
            Map.entry("Stream", "java.util.stream.Stream"),
            Map.entry("Collectors", "java.util.stream.Collectors"),
            Map.entry("String", "java.lang.String"),
            Map.entry("Integer", "java.lang.Integer"),
            Map.entry("Long", "java.lang.Long"),
            Map.entry("Double", "java.lang.Double"),
            Map.entry("Boolean", "java.lang.Boolean"),
            Map.entry("Object", "java.lang.Object"),
            Map.entry("Class", "java.lang.Class"),
            Map.entry("Exception", "java.lang.Exception"),
            Map.entry("RuntimeException", "java.lang.RuntimeException"),
            Map.entry("Objects", "java.util.Objects"),
            Map.entry("Files", "java.nio.file.Files"),
            Map.entry("Paths", "java.nio.file.Paths"),
            Map.entry("Path", "java.nio.file.Path"),
            Map.entry("System", "java.lang.System"),
            Map.entry("Math", "java.lang.Math")
    );
    
    private static final Map<String, String> COMMON_SPRING_TYPES = Map.ofEntries(
            Map.entry("RestTemplate", "org.springframework.web.client.RestTemplate"),
            Map.entry("WebClient", "org.springframework.web.reactive.function.client.WebClient"),
            Map.entry("JdbcTemplate", "org.springframework.jdbc.core.JdbcTemplate"),
            Map.entry("NamedParameterJdbcTemplate", "org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate"),
            Map.entry("KafkaTemplate", "org.springframework.kafka.core.KafkaTemplate"),
            Map.entry("ApplicationContext", "org.springframework.context.ApplicationContext"),
            Map.entry("Environment", "org.springframework.core.env.Environment")
    );
    
    @Override
    public Optional<String> resolveType(String simpleName, ResolutionContext context) {
        // 1. Check explicit imports
        if (context.imports().containsKey(simpleName)) {
            return Optional.of(context.imports().get(simpleName));
        }
        
        // 2. Check JDK types
        if (COMMON_JDK_TYPES.containsKey(simpleName)) {
            return Optional.of(COMMON_JDK_TYPES.get(simpleName));
        }
        
        // 3. Check Spring types
        if (COMMON_SPRING_TYPES.containsKey(simpleName)) {
            return Optional.of(COMMON_SPRING_TYPES.get(simpleName));
        }
        
        // 4. Assume same package
        if (!context.packageName().isEmpty()) {
            return Optional.of(context.packageName() + "." + simpleName);
        }
        
        return Optional.empty();
    }
    
    @Override
    public Optional<String> resolveMethodCall(String scope, String methodName, ResolutionContext context) {
        // Implicit this call
        if (scope == null || scope.isEmpty()) {
            if (context.containingType() != null) {
                return Optional.of(context.containingType().fullyQualifiedName() + "#" + methodName);
            }
            return Optional.empty();
        }
        
        // Extract root variable from chained calls
        String rootScope = extractRootScope(scope);
        
        // 1. Check local variables
        if (context.localVariables().containsKey(rootScope)) {
            TypeRef type = context.localVariables().get(rootScope);
            return Optional.of(type.fullyQualifiedName() + "#" + methodName);
        }
        
        // 2. Check lambda parameters
        if (context.lambdaParameters().containsKey(rootScope)) {
            TypeRef type = context.lambdaParameters().get(rootScope);
            return Optional.of(type.fullyQualifiedName() + "#" + methodName);
        }
        
        // 3. Check method parameters
        if (context.containingMethod() != null) {
            for (var param : context.containingMethod().parameters()) {
                if (param.name().equals(rootScope)) {
                    return Optional.of(param.type().fullyQualifiedName() + "#" + methodName);
                }
            }
        }
        
        // 4. Check if scope is a type (static call)
        Optional<String> resolvedType = resolveType(rootScope, context);
        if (resolvedType.isPresent()) {
            return Optional.of(resolvedType.get() + "#" + methodName);
        }
        
        // 5. Lombok log
        if ("log".equals(rootScope)) {
            return Optional.of("org.slf4j.Logger#" + methodName);
        }
        
        log.debug("Could not resolve method call: {}.{}", scope, methodName);
        return Optional.empty();
    }
    
    private String extractRootScope(String fullScope) {
        String scope = fullScope;
        if (fullScope.contains(".")) {
            scope = fullScope.substring(0, fullScope.indexOf('.'));
        }
        if (fullScope.contains("(") && !fullScope.startsWith("(")) {
            String beforeParen = fullScope.substring(0, fullScope.indexOf('('));
            if (beforeParen.contains(".")) {
                scope = beforeParen.substring(0, beforeParen.indexOf('.'));
            } else {
                scope = beforeParen;
            }
        }
        return scope;
    }
}
