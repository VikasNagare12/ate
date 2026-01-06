package com.vidnyan.ate.application.port.out;

import com.vidnyan.ate.domain.model.*;

import java.util.Map;
import java.util.Optional;

/**
 * Port for resolving types and method calls to FQNs.
 * Implemented by adapters that have access to type information.
 */
public interface TypeResolver {
    
    /**
     * Resolve a simple type name to its FQN.
     */
    Optional<String> resolveType(String simpleName, ResolutionContext context);
    
    /**
     * Resolve a method call to its target FQN.
     */
    Optional<String> resolveMethodCall(String scope, String methodName, ResolutionContext context);
    
    /**
     * Context for type resolution.
     */
    record ResolutionContext(
        String packageName,
        Map<String, String> imports,           // simple name -> FQN
        TypeEntity containingType,
        MethodEntity containingMethod,
        Map<String, TypeRef> localVariables,   // name -> type
        Map<String, TypeRef> lambdaParameters  // name -> type
    ) {
        public static ResolutionContext forType(TypeEntity type) {
            return new ResolutionContext(
                    type.packageName(), Map.of(), type, null, Map.of(), Map.of());
        }
        
        public ResolutionContext withMethod(MethodEntity method) {
            return new ResolutionContext(
                    packageName, imports, containingType, method, localVariables, lambdaParameters);
        }
        
        public ResolutionContext withLocalVariable(String name, TypeRef type) {
            var newLocals = new java.util.HashMap<>(localVariables);
            newLocals.put(name, type);
            return new ResolutionContext(
                    packageName, imports, containingType, containingMethod, newLocals, lambdaParameters);
        }
    }
}
