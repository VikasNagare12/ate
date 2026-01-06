package com.vidnyan.ate.model.builder;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.*;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import com.vidnyan.ate.model.*;
import com.vidnyan.ate.model.Parameter;
import com.vidnyan.ate.parser.AstParser;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Builds the canonical Source Model from parsed ASTs.
 * This is the MOST IMPORTANT component - transforms ASTs into the immutable Source Model.
 */
@Slf4j
@Component
public class SourceModelBuilder {
    
    private final Map<String, Type> types = new HashMap<>();
    private final Map<String, Method> methods = new HashMap<>();
    private final Map<String, Field> fields = new HashMap<>();
    private final Map<String, Annotation> annotations = new HashMap<>();
    private final List<Relationship> relationships = new ArrayList<>();
    
    private final Map<String, List<Type>> typesByPackage = new HashMap<>();
    private final Map<String, List<Method>> methodsByAnnotation = new HashMap<>();
    private final Map<String, List<Type>> typesByAnnotation = new HashMap<>();
    private final Map<String, List<Field>> fieldsByAnnotation = new HashMap<>();
    private final Map<String, List<Relationship>> relationshipsBySource = new HashMap<>();
    private final Map<String, List<Relationship>> relationshipsByTarget = new HashMap<>();
    
    // Store AST nodes for method call extraction
    private final Map<String, MethodDeclaration> methodAstNodes = new HashMap<>();
    private final Map<String, CompilationUnit> compilationUnits = new HashMap<>();
    
    /**
     * Build Source Model from parse results.
     */
    public SourceModel build(List<AstParser.ParseResult> parseResults) {
        log.info("Building Source Model from {} files", parseResults.size());

        // Phase 1: Extract entities from ASTs
        for (AstParser.ParseResult result : parseResults) {
            if (result.isSuccess()) {
                CompilationUnit cu = result.getCompilationUnit();
                extractEntities(cu, result.getFilePath());
                compilationUnits.put(result.getFilePath().toString(), cu);
            }
        }
        
        // Phase 2: Extract method calls
        extractMethodCalls();
        
        // Phase 3: Resolve symbols and relationships
        resolveRelationships();
        
        // Phase 4: Build indexes
        buildIndexes();
        
        // Phase 5: Enrich metadata
        enrichMetadata();
        
        // Phase 6: Freeze model
        return freezeModel();
    }
    
    /**
     * Extract entities (types, methods, fields) from a compilation unit.
     */
    private void extractEntities(CompilationUnit cu, java.nio.file.Path filePath) {
        String packageName = cu.getPackageDeclaration()
                .map(pd -> pd.getNameAsString())
                .orElse("");
        
        List<ImportDeclaration> imports = cu.getImports();

        cu.accept(new VoidVisitorAdapter<Void>() {
            @Override
            public void visit(ClassOrInterfaceDeclaration n, Void arg) {
                Type type = extractType(n, packageName, filePath, imports);
                types.put(type.getFullyQualifiedName(), type);
                super.visit(n, arg);
            }
            
            @Override
            public void visit(EnumDeclaration n, Void arg) {
                Type type = extractEnum(n, packageName, filePath, imports);
                types.put(type.getFullyQualifiedName(), type);
                super.visit(n, arg);
            }
        }, null);
    }
    
    /**
     * Resolve type simple name to fully qualified name using imports and package.
     */
    private String resolveType(String typeName, List<ImportDeclaration> imports,
            String packageName) {
        // If already fully qualified, return it
        if (typeName.contains(".")) {
            return typeName;
        }

        // Check standard types (simplified)
        if (typeName.equals("String") || typeName.equals("Integer") || typeName.equals("Boolean") ||
                typeName.equals("Long") || typeName.equals("Double") || typeName.equals("Float") ||
                typeName.equals("Object") || typeName.equals("Class") || typeName.equals("Void")) {
            return "java.lang." + typeName;
        }

        // Check imports
        for (var imp : imports) {
            String importedName = imp.getNameAsString();
            boolean isStatic = imp.isStatic();
            boolean isAsterisk = imp.isAsterisk();

            if (!isStatic && !isAsterisk) {
                // Exact match import: import com.foo.Bar;
                if (importedName.endsWith("." + typeName)) {
                    return importedName;
                }
            } else if (isAsterisk) {
                // Wildcard import: import com.foo.*;
                // We can't know for sure without scanning classpath, but we can't resolve it
                // strictly.
                // Leave as is or assume package? For now, ignore wildcard resolution.
            }
        }

        // Assume same package if not found in imports
        // (This might be wrong for classes in java.lang, but we handled common ones)
        if (!packageName.isEmpty()) {
            return packageName + "." + typeName;
        }

        return typeName;
    }

    /**
     * Extract annotations from annotation expressions.
     */
    private List<com.vidnyan.ate.model.Annotation> extractAnnotations(
            List<AnnotationExpr> annotationExprs, java.nio.file.Path filePath,
            List<ImportDeclaration> imports, String packageName) {
        return annotationExprs.stream()
                .map(ae -> {
                    String name = ae.getNameAsString();
                    String fqn = resolveType(name, imports, packageName);

                    Map<String, Object> values = new HashMap<>();
                    // TODO: Extract annotation values

                    return com.vidnyan.ate.model.Annotation.builder()
                            .name(name)
                            .fullyQualifiedName(fqn)
                            .values(values)
                            .location(Location.builder()
                                    .filePath(filePath.toString())
                                    .line(ae.getBegin().map(p -> p.line).orElse(0))
                                    .column(ae.getBegin().map(p -> p.column).orElse(0))
                                    .build())
                            .build();
                })
                .toList();
    }

    /**
     * Extract a Type from a ClassOrInterfaceDeclaration.
     */
    /**
     * Extract a Type from a ClassOrInterfaceDeclaration.
     */
    private Type extractType(ClassOrInterfaceDeclaration decl, String packageName, java.nio.file.Path filePath,
            List<ImportDeclaration> imports) {
        String simpleName = decl.getNameAsString();
        String fqn = packageName.isEmpty() ? simpleName : packageName + "." + simpleName;
        
        TypeKind kind = decl.isInterface() ? TypeKind.INTERFACE : TypeKind.CLASS;
        if (decl.isAnnotationDeclaration()) {
            kind = TypeKind.ANNOTATION;
        }
        Set<Modifier> modifiers = extractModifiers(decl);
        List<com.vidnyan.ate.model.Annotation> typeAnnotations = extractAnnotations(decl.getAnnotations(), filePath,
                imports, packageName);
        
        // Extract super types
        List<TypeRef> superTypes = new ArrayList<>();
        if (decl.getExtendedTypes().isNonEmpty()) {
            decl.getExtendedTypes().forEach(et -> 
            superTypes.add(TypeRef.of(resolveType(et.getNameAsString(), imports, packageName))));
        }
        if (decl.getImplementedTypes().isNonEmpty()) {
            decl.getImplementedTypes().forEach(it -> 
            superTypes.add(TypeRef.of(resolveType(it.getNameAsString(), imports, packageName))));
        }
        
        // Extract methods
        List<Method> typeMethods = new ArrayList<>();
        for (MethodDeclaration methodDecl : decl.getMethods()) {
            Method method = extractMethod(methodDecl, fqn, filePath, imports, packageName);
            typeMethods.add(method);
            methods.put(method.getFullyQualifiedName(), method);
            // Store AST node for later method call extraction
            methodAstNodes.put(method.getFullyQualifiedName(), methodDecl);
        }
        
        // Extract fields
        List<Field> typeFields = new ArrayList<>();
        for (FieldDeclaration fieldDecl : decl.getFields()) {
            for (VariableDeclarator var : fieldDecl.getVariables()) {
                Field field = extractField(var, fieldDecl, fqn, filePath, imports, packageName);
                typeFields.add(field);
                fields.put(field.getFullyQualifiedName(), field);
            }
        }
        
        // Detect Spring annotations
        boolean isSpringComponent = typeAnnotations.stream()
                .anyMatch(a -> a.getName().contains("Component") || 
                              a.getName().contains("Service") ||
                              a.getName().contains("Repository") ||
                              a.getName().contains("Controller"));
        boolean isSpringConfiguration = typeAnnotations.stream()
                .anyMatch(a -> a.getName().contains("Configuration"));
        
        return Type.builder()
                .simpleName(simpleName)
                .fullyQualifiedName(fqn)
                .packageName(packageName)
                .kind(kind)
                .modifiers(modifiers)
                .annotations(typeAnnotations)
                .superTypes(superTypes)
                .methods(typeMethods)
                .fields(typeFields)
                .location(Location.builder()
                        .filePath(filePath.toString())
                        .line(decl.getBegin().map(p -> p.line).orElse(0))
                        .column(decl.getBegin().map(p -> p.column).orElse(0))
                        .build())
                .isSpringComponent(isSpringComponent)
                .isSpringConfiguration(isSpringConfiguration)
                .build();
    }
    
    /**
     * Extract a Type from an EnumDeclaration.
     */
    private Type extractEnum(EnumDeclaration decl, String packageName, java.nio.file.Path filePath,
            List<ImportDeclaration> imports) {
        String simpleName = decl.getNameAsString();
        String fqn = packageName.isEmpty() ? simpleName : packageName + "." + simpleName;
        
        Set<Modifier> modifiers = extractModifiers(decl);
        List<com.vidnyan.ate.model.Annotation> typeAnnotations = extractAnnotations(decl.getAnnotations(), filePath,
                imports, packageName);
        
        // Enums can have methods and fields too
        List<Method> typeMethods = new ArrayList<>();
        for (MethodDeclaration methodDecl : decl.getMethods()) {
            Method method = extractMethod(methodDecl, fqn, filePath, imports, packageName);
            typeMethods.add(method);
            methods.put(method.getFullyQualifiedName(), method);
            // Store AST node for later method call extraction
            methodAstNodes.put(method.getFullyQualifiedName(), methodDecl);
        }
        
        List<Field> typeFields = new ArrayList<>();
        for (FieldDeclaration fieldDecl : decl.getFields()) {
            for (VariableDeclarator var : fieldDecl.getVariables()) {
                Field field = extractField(var, fieldDecl, fqn, filePath, imports, packageName);
                typeFields.add(field);
                fields.put(field.getFullyQualifiedName(), field);
            }
        }
        
        return Type.builder()
                .simpleName(simpleName)
                .fullyQualifiedName(fqn)
                .packageName(packageName)
                .kind(TypeKind.ENUM)
                .modifiers(modifiers)
                .annotations(typeAnnotations)
                .superTypes(List.of()) // Enums extend Enum<E>
                .methods(typeMethods)
                .fields(typeFields)
                .location(Location.builder()
                        .filePath(filePath.toString())
                        .line(decl.getBegin().isPresent() ? decl.getBegin().get().line : 0)
                        .column(decl.getBegin().isPresent() ? decl.getBegin().get().column : 0)
                        .build())
                .isSpringComponent(false)
                .isSpringConfiguration(false)
                .build();
    }
    
    /**
     * Extract a Method from a MethodDeclaration.
     */
    private Method extractMethod(MethodDeclaration decl, String containingTypeFqn, java.nio.file.Path filePath,
            List<ImportDeclaration> imports, String packageName) {
        String methodName = decl.getNameAsString();
        String signature = buildMethodSignature(decl);
        String fqn = containingTypeFqn + "#" + signature;
        
        TypeRef returnType = TypeRef.of(resolveType(decl.getType().asString(), imports, packageName));
        
        List<Parameter> parameters = decl.getParameters().stream()
                .map(p -> Parameter.builder()
                        .name(p.getNameAsString())
                        .type(TypeRef.of(resolveType(p.getType().asString(), imports, packageName)))
                        .isVarArgs(p.isVarArgs())
                        .location(Location.builder()
                                .filePath(filePath.toString())
                                .line(p.getBegin().map(b -> b.line).orElse(0))
                                .column(p.getBegin().map(b -> b.column).orElse(0))
                                .build())
                        .build())
                .toList();
        
        Set<Modifier> modifiers = extractModifiers(decl);
        List<com.vidnyan.ate.model.Annotation> methodAnnotations = extractAnnotations(decl.getAnnotations(), filePath,
                imports, packageName);
        
        // Detect Spring annotations
        boolean isTransactional = methodAnnotations.stream()
                .anyMatch(a -> a.getName().equals("Transactional"));
        boolean isScheduled = methodAnnotations.stream()
                .anyMatch(a -> a.getName().equals("Scheduled"));
        boolean isAutowired = methodAnnotations.stream()
                .anyMatch(a -> a.getName().equals("Autowired"));
        
        return Method.builder()
                .name(methodName)
                .signature(signature)
                .fullyQualifiedName(fqn)
                .returnType(returnType)
                .parameters(parameters)
                .modifiers(modifiers)
                .annotations(methodAnnotations)
                .location(Location.builder()
                        .filePath(filePath.toString())
                        .line(decl.getBegin().map(p -> p.line).orElse(0))
                        .column(decl.getBegin().map(p -> p.column).orElse(0))
                        .build())
                .containingTypeFqn(containingTypeFqn)
                .isSpringBean(false) // Will be set from containing type
                .isTransactional(isTransactional)
                .isScheduled(isScheduled)
                .isAutowired(isAutowired)
                .cyclomaticComplexity(1) // TODO: Compute actual complexity
                .build();
    }
    
    /**
     * Extract a Field from a VariableDeclarator.
     */
    private Field extractField(VariableDeclarator var, FieldDeclaration fieldDecl, 
            String containingTypeFqn, java.nio.file.Path filePath,
            List<ImportDeclaration> imports, String packageName) {
        String fieldName = var.getNameAsString();
        String fqn = containingTypeFqn + "." + fieldName;
        
        TypeRef type = TypeRef.of(resolveType(fieldDecl.getElementType().asString(), imports, packageName));
        Set<Modifier> modifiers = extractModifiers(fieldDecl);
        List<com.vidnyan.ate.model.Annotation> fieldAnnotations = extractAnnotations(fieldDecl.getAnnotations(),
                filePath, imports, packageName);
        
        return Field.builder()
                .name(fieldName)
                .fullyQualifiedName(fqn)
                .type(type)
                .modifiers(modifiers)
                .annotations(fieldAnnotations)
                .location(Location.builder()
                        .filePath(filePath.toString())
                        .line(var.getBegin().map(p -> p.line).orElse(0))
                        .column(var.getBegin().map(p -> p.column).orElse(0))
                        .build())
                .containingTypeFqn(containingTypeFqn)
                .build();
    }
    
    /**
     * Build method signature string.
     */
    private String buildMethodSignature(MethodDeclaration decl) {
        String params = decl.getParameters().stream()
                .map(p -> p.getType().asString())
                .collect(Collectors.joining(","));
        return decl.getNameAsString() + "(" + params + ")";
    }
    
    /**
     * Extract modifiers from a node that implements NodeWithModifiers.
     */
    private Set<Modifier> extractModifiers(com.github.javaparser.ast.body.BodyDeclaration<?> decl) {
        Set<Modifier> modifiers = new HashSet<>();
        
        // Check if the node implements NodeWithModifiers interface
        if (decl instanceof com.github.javaparser.ast.nodeTypes.NodeWithModifiers<?>) {
            @SuppressWarnings("unchecked")
            com.github.javaparser.ast.nodeTypes.NodeWithModifiers<?> nodeWithModifiers = 
                (com.github.javaparser.ast.nodeTypes.NodeWithModifiers<?>) decl;
            
            // Get modifiers from the node
            for (com.github.javaparser.ast.Modifier mod : nodeWithModifiers.getModifiers()) {
                // JavaParser Modifier has a getKeyword() method that returns Keyword enum
                com.github.javaparser.ast.Modifier.Keyword keyword = mod.getKeyword();
                switch (keyword) {
                    case PUBLIC -> modifiers.add(Modifier.PUBLIC);
                    case PRIVATE -> modifiers.add(Modifier.PRIVATE);
                    case PROTECTED -> modifiers.add(Modifier.PROTECTED);
                    case STATIC -> modifiers.add(Modifier.STATIC);
                    case FINAL -> modifiers.add(Modifier.FINAL);
                    case ABSTRACT -> modifiers.add(Modifier.ABSTRACT);
                    case SYNCHRONIZED -> modifiers.add(Modifier.SYNCHRONIZED);
                    case VOLATILE -> modifiers.add(Modifier.VOLATILE);
                    case TRANSIENT -> modifiers.add(Modifier.TRANSIENT);
                    case NATIVE -> modifiers.add(Modifier.NATIVE);
                    case STRICTFP -> modifiers.add(Modifier.STRICTFP);
                    default -> {
                        // Ignore other modifiers
                    }
                }
            }
            
            // If no access modifier is present, it's package-private
            boolean hasAccessModifier = modifiers.contains(Modifier.PUBLIC) ||
                                       modifiers.contains(Modifier.PRIVATE) ||
                                       modifiers.contains(Modifier.PROTECTED);
            if (!hasAccessModifier) {
                modifiers.add(Modifier.PACKAGE_PRIVATE);
            }
        }
        
        return modifiers;
    }


    
    /**
     * Extract method calls from method bodies.
     */
    private void extractMethodCalls() {
        log.info("Extracting method calls from {} methods", methodAstNodes.size());
        
        for (Map.Entry<String, MethodDeclaration> entry : methodAstNodes.entrySet()) {
            String callerFqn = entry.getKey();
            MethodDeclaration methodDecl = entry.getValue();
            
            // Skip methods without body (abstract, interface methods)
            if (!methodDecl.getBody().isPresent()) {
                continue;
            }
            
            // Find containing type to resolve fields
            String containingTypeFqn = methods.get(callerFqn).getContainingTypeFqn();
            Type containingType = types.get(containingTypeFqn);

            // Get method parameters for resolution
            Method callerMethod = methods.get(callerFqn);
            Map<String, String> parameterTypes = new HashMap<>();
            if (callerMethod != null) {
                for (Parameter param : callerMethod.getParameters()) {
                    parameterTypes.put(param.getName(), param.getType().getFullyQualifiedName());
                }
            }

            // Track local variable types as we visit
            Map<String, String> localVariableTypes = new HashMap<>();

            // Visit method body to find method calls
            methodDecl.getBody().get().accept(new VoidVisitorAdapter<Void>() {

                @Override
                public void visit(com.github.javaparser.ast.expr.VariableDeclarationExpr varDecl, Void arg) {
                    super.visit(varDecl, arg);
                    // Track local variable types
                    for (var v : varDecl.getVariables()) {
                        String varName = v.getNameAsString();
                        String typeName = varDecl.getElementType().asString();
                        // Try to resolve type name using imports (simplified - use as-is for now)
                        localVariableTypes.put(varName, typeName);
                    }
                }

                @Override
                public void visit(com.github.javaparser.ast.expr.MethodCallExpr call, Void arg) {
                    super.visit(call, arg);
                    
                    // Build callee signature (best effort)
                    String methodName = call.getNameAsString();
                    String calleeSignature = buildCalleeSignature(call);
                    String resolvedCallee = null;

                    // Try to resolve FQN
                    if (call.getScope().isPresent()) {
                        String fullScope = call.getScope().get().toString();

                        // Extract the ROOT variable from chained calls like
                        // "evaluators.stream().filter()"
                        // The root is "evaluators" in this case
                        String scope = fullScope;
                        if (fullScope.contains(".")) {
                            scope = fullScope.substring(0, fullScope.indexOf('.'));
                        }
                        if (fullScope.contains("(") && !fullScope.startsWith("(")) {
                            // Could be a method call chain, extract before first paren
                            String beforeParen = fullScope.substring(0, fullScope.indexOf('('));
                            if (beforeParen.contains(".")) {
                                scope = beforeParen.substring(0, beforeParen.indexOf('.'));
                            } else {
                                scope = beforeParen;
                            }
                        }

                        log.debug("Resolving call: {} with fullScope: {}, extracted scope: {}", calleeSignature,
                                fullScope, scope);

                        // 1. Check class fields
                        if (containingType != null && resolvedCallee == null) {
                            log.debug("  Checking {} fields in {}", containingType.getFields().size(),
                                    containingType.getSimpleName());
                            for (Field field : containingType.getFields()) {
                                log.debug("    Field: {} vs scope: {}", field.getName(), scope);
                                if (field.getName().equals(scope)) {
                                    String fieldType = field.getType().getFullyQualifiedName();
                                    String params = calleeSignature.substring(calleeSignature.indexOf('('));
                                    resolvedCallee = fieldType + "#" + methodName + params;
                                    log.debug("  Resolved via field: {}", resolvedCallee);
                                    break;
                                }
                            }
                        }

                        // 2. Check method parameters
                        if (resolvedCallee == null && parameterTypes.containsKey(scope)) {
                            String paramType = parameterTypes.get(scope);
                            String params = calleeSignature.substring(calleeSignature.indexOf('('));
                            resolvedCallee = paramType + "#" + methodName + params;
                            log.debug("  Resolved via parameter: {}", resolvedCallee);
                        }

                        // 3. Check local variables
                        if (resolvedCallee == null && localVariableTypes.containsKey(scope)) {
                            String localType = localVariableTypes.get(scope);
                            String params = calleeSignature.substring(calleeSignature.indexOf('('));
                            // localType may not be FQN, but it's better than null
                            resolvedCallee = localType + "#" + methodName + params;
                            log.debug("  Resolved via local var: {}", resolvedCallee);
                        }

                        // 4. Check if scope is a known type (static call to our types)
                        if (resolvedCallee == null) {
                            for (Type t : types.values()) {
                                if (t.getSimpleName().equals(scope)) {
                                    String params = calleeSignature.substring(calleeSignature.indexOf('('));
                                    resolvedCallee = t.getFullyQualifiedName() + "#" + methodName + params;
                                    log.debug("  Resolved via static type: {}", resolvedCallee);
                                    break;
                                }
                            }
                        }

                        // 5. Check Lombok-generated log field
                        if (resolvedCallee == null && "log".equals(scope)) {
                            String params = calleeSignature.substring(calleeSignature.indexOf('('));
                            resolvedCallee = "org.slf4j.Logger#" + methodName + params;
                            log.debug("  Resolved via Lombok log: {}", resolvedCallee);
                        }

                        // 6. Check common JDK types for static calls
                        if (resolvedCallee == null) {
                            String jdkType = resolveCommonJdkType(scope);
                            if (jdkType != null) {
                                String params = calleeSignature.substring(calleeSignature.indexOf('('));
                                resolvedCallee = jdkType + "#" + methodName + params;
                                log.debug("  Resolved via JDK type: {}", resolvedCallee);
                            }
                        }

                        if (resolvedCallee == null) {
                            log.debug("  UNRESOLVED - scope: {}", scope);
                        }
                    } else {
                        // Implicit 'this' call
                        String params = calleeSignature.substring(calleeSignature.indexOf('('));
                        resolvedCallee = containingTypeFqn + "#" + methodName + params;
                        log.debug("Resolved implicit this call: {}", resolvedCallee);
                    }
                    
                    // Determine call type
                    Relationship.CallType callType = determineCallType(call);
                    
                    // Create CALLS relationship
                    relationships.add(Relationship.builder()
                            .type(RelationshipType.CALLS)
                            .sourceEntityId(callerFqn)
                            .targetEntityId(calleeSignature)
                            .resolvedTargetEntityId(resolvedCallee)
                            .callType(callType)
                            .location(Location.builder()
                                    .filePath(methodDecl.findCompilationUnit()
                                            .flatMap(cu -> cu.getStorage())
                                            .map(s -> s.getPath().toString())
                                            .orElse("unknown"))
                                    .line(call.getBegin().map(p -> p.line).orElse(0))
                                    .column(call.getBegin().map(p -> p.column).orElse(0))
                                    .build())
                            .build());
                }
                
                @Override
                public void visit(com.github.javaparser.ast.expr.ObjectCreationExpr creation, Void arg) {
                    super.visit(creation, arg);
                    
                    // Constructor calls
                    String typeName = creation.getType().getNameAsString();
                    // Resolve type name? It might be simple name.
                    // types map has FQNs. We need to resolve typeName in context of this file.
                    // But we don't have imports here easily unless we passed them or re-parsed.
                    // For now, leave as best effort.

                    String constructorSignature = buildConstructorSignature(creation);
                    
                    relationships.add(Relationship.builder()
                            .type(RelationshipType.CALLS)
                            .sourceEntityId(callerFqn)
                            .targetEntityId(constructorSignature)
                            .callType(Relationship.CallType.DIRECT)
                            .location(Location.builder()
                                    .filePath(methodDecl.findCompilationUnit()
                                            .flatMap(cu -> cu.getStorage())
                                            .map(s -> s.getPath().toString())
                                            .orElse("unknown"))
                                    .line(creation.getBegin().map(p -> p.line).orElse(0))
                                    .column(creation.getBegin().map(p -> p.column).orElse(0))
                                    .build())
                            .build());
                }
            }, null);
        }
        
        log.info("Extracted {} CALLS relationships", 
                relationships.stream().filter(r -> r.getType() == RelationshipType.CALLS).count());
    }
    
    /**
     * Build callee signature from method call expression.
     */
    private String buildCalleeSignature(com.github.javaparser.ast.expr.MethodCallExpr call) {
        String methodName = call.getNameAsString();
        
        // Try to resolve the scope to get the type
        String scope = "";
        if (call.getScope().isPresent()) {
            scope = call.getScope().get().toString();
        }
        
        // Build parameter types (simplified - just count)
        String params = call.getArguments().stream()
                .map(arg -> "?")
                .collect(Collectors.joining(","));
        
        // Return simplified signature
        // Format: scope.methodName(params) or just methodName(params)
        if (!scope.isEmpty()) {
            return scope + "." + methodName + "(" + params + ")";
        }
        return methodName + "(" + params + ")";
    }
    
    /**
     * Resolve common JDK types for static method calls.
     */
    private String resolveCommonJdkType(String simpleName) {
        return switch (simpleName) {
            case "List" -> "java.util.List";
            case "Set" -> "java.util.Set";
            case "Map" -> "java.util.Map";
            case "Arrays" -> "java.util.Arrays";
            case "Collections" -> "java.util.Collections";
            case "Optional" -> "java.util.Optional";
            case "Stream" -> "java.util.stream.Stream";
            case "Collectors" -> "java.util.stream.Collectors";
            case "String" -> "java.lang.String";
            case "Integer" -> "java.lang.Integer";
            case "Long" -> "java.lang.Long";
            case "Double" -> "java.lang.Double";
            case "Boolean" -> "java.lang.Boolean";
            case "Math" -> "java.lang.Math";
            case "System" -> "java.lang.System";
            case "Objects" -> "java.util.Objects";
            case "Files" -> "java.nio.file.Files";
            case "Paths" -> "java.nio.file.Paths";
            case "Path" -> "java.nio.file.Path";
            default -> null;
        };
    }

    /**
     * Build constructor signature from object creation expression.
     */
    private String buildConstructorSignature(com.github.javaparser.ast.expr.ObjectCreationExpr creation) {
        String typeName = creation.getType().getNameAsString();
        String params = creation.getArguments().stream()
                .map(arg -> "?")
                .collect(Collectors.joining(","));
        return typeName + ".<init>(" + params + ")";
    }
    
    /**
     * Determine the call type based on the call expression.
     */
    private Relationship.CallType determineCallType(com.github.javaparser.ast.expr.MethodCallExpr call) {
        // Simple heuristic: if there's a scope, it's likely virtual
        // More sophisticated analysis would require type resolution
        if (call.getScope().isPresent()) {
            return Relationship.CallType.VIRTUAL;
        }
        return Relationship.CallType.DIRECT;
    }
    
    /**
     * Resolve relationships between entities.
     */
    private void resolveRelationships() {
        // Create CONTAINS relationships
        for (Type type : types.values()) {
            for (Method method : type.getMethods()) {
                relationships.add(Relationship.builder()
                        .type(RelationshipType.CONTAINS)
                        .sourceEntityId(type.getFullyQualifiedName())
                        .targetEntityId(method.getFullyQualifiedName())
                        .location(method.getLocation())
                        .build());
            }
            for (Field field : type.getFields()) {
                relationships.add(Relationship.builder()
                        .type(RelationshipType.CONTAINS)
                        .sourceEntityId(type.getFullyQualifiedName())
                        .targetEntityId(field.getFullyQualifiedName())
                        .location(field.getLocation())
                        .build());
            }
        }
    }
    
    /**
     * Build indexes for fast lookups.
     */
    private void buildIndexes() {
        // Index by package
        for (Type type : types.values()) {
            typesByPackage.computeIfAbsent(type.getPackageName(), k -> new ArrayList<>()).add(type);
        }
        
        // Index by annotation
        for (Method method : methods.values()) {
            for (com.vidnyan.ate.model.Annotation ann : method.getAnnotations()) {
                methodsByAnnotation.computeIfAbsent(ann.getName(), k -> new ArrayList<>()).add(method);
                methodsByAnnotation.computeIfAbsent(ann.getFullyQualifiedName(), k -> new ArrayList<>()).add(method);
            }
        }
        
        for (Type type : types.values()) {
            for (com.vidnyan.ate.model.Annotation ann : type.getAnnotations()) {
                typesByAnnotation.computeIfAbsent(ann.getName(), k -> new ArrayList<>()).add(type);
                typesByAnnotation.computeIfAbsent(ann.getFullyQualifiedName(), k -> new ArrayList<>()).add(type);
            }
        }
        
        for (Field field : fields.values()) {
            for (com.vidnyan.ate.model.Annotation ann : field.getAnnotations()) {
                fieldsByAnnotation.computeIfAbsent(ann.getName(), k -> new ArrayList<>()).add(field);
                fieldsByAnnotation.computeIfAbsent(ann.getFullyQualifiedName(), k -> new ArrayList<>()).add(field);
            }
        }
        
        // Index relationships
        for (Relationship rel : relationships) {
            relationshipsBySource.computeIfAbsent(rel.getSourceEntityId(), k -> new ArrayList<>()).add(rel);
            relationshipsByTarget.computeIfAbsent(rel.getTargetEntityId(), k -> new ArrayList<>()).add(rel);
        }
    }
    
    /**
     * Enrich entities with metadata.
     */
    private void enrichMetadata() {
        // Mark methods as Spring beans if containing type is a Spring component
        for (Method method : methods.values()) {
            Type containingType = types.get(method.getContainingTypeFqn());
            if (containingType != null && containingType.isSpringComponent()) {
                // Create a new method with updated metadata (since Method is immutable)
                // For now, we'll handle this in queries
            }
        }
    }
    
    /**
     * Freeze the model - make it immutable.
     */
    private SourceModel freezeModel() {
        return SourceModel.builder()
                .types(Collections.unmodifiableMap(types))
                .methods(Collections.unmodifiableMap(methods))
                .fields(Collections.unmodifiableMap(fields))
                .annotations(Collections.unmodifiableMap(annotations))
                .relationships(Collections.unmodifiableList(relationships))
                .typesByPackage(Collections.unmodifiableMap(typesByPackage))
                .methodsByAnnotation(Collections.unmodifiableMap(methodsByAnnotation))
                .typesByAnnotation(Collections.unmodifiableMap(typesByAnnotation))
                .fieldsByAnnotation(Collections.unmodifiableMap(fieldsByAnnotation))
                .relationshipsBySource(Collections.unmodifiableMap(relationshipsBySource))
                .relationshipsByTarget(Collections.unmodifiableMap(relationshipsByTarget))
                .isFrozen(true)
                .build();
    }
}

