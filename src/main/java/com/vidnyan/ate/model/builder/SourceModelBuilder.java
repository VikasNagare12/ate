package com.vidnyan.ate.model.builder;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.*;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import com.vidnyan.ate.model.*;
import com.vidnyan.ate.parser.AstParser;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Builds the canonical Source Model from parsed ASTs.
 * This is the MOST IMPORTANT component - transforms ASTs into the immutable Source Model.
 */
@Slf4j
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
    
    /**
     * Build Source Model from parse results.
     */
    public SourceModel build(List<AstParser.ParseResult> parseResults) {
        log.info("Building Source Model from {} files", parseResults.size());
        
        // Phase 1: Extract entities from ASTs
        for (AstParser.ParseResult result : parseResults) {
            if (result.isSuccess()) {
                extractEntities(result.getCompilationUnit(), result.getFilePath());
            }
        }
        
        // Phase 2: Resolve symbols and relationships
        resolveRelationships();
        
        // Phase 3: Build indexes
        buildIndexes();
        
        // Phase 4: Enrich metadata
        enrichMetadata();
        
        // Phase 5: Freeze model
        return freezeModel();
    }
    
    /**
     * Extract entities (types, methods, fields) from a compilation unit.
     */
    private void extractEntities(CompilationUnit cu, java.nio.file.Path filePath) {
        String packageName = cu.getPackageDeclaration()
                .map(pd -> pd.getNameAsString())
                .orElse("");
        
        cu.accept(new VoidVisitorAdapter<Void>() {
            @Override
            public void visit(ClassOrInterfaceDeclaration n, Void arg) {
                Type type = extractType(n, packageName, filePath);
                types.put(type.getFullyQualifiedName(), type);
                super.visit(n, arg);
            }
            
            @Override
            public void visit(EnumDeclaration n, Void arg) {
                Type type = extractEnum(n, packageName, filePath);
                types.put(type.getFullyQualifiedName(), type);
                super.visit(n, arg);
            }
        }, null);
    }
    
    /**
     * Extract a Type from a ClassOrInterfaceDeclaration.
     */
    private Type extractType(ClassOrInterfaceDeclaration decl, String packageName, java.nio.file.Path filePath) {
        String simpleName = decl.getNameAsString();
        String fqn = packageName.isEmpty() ? simpleName : packageName + "." + simpleName;
        
        TypeKind kind = decl.isInterface() ? TypeKind.INTERFACE : TypeKind.CLASS;
        if (decl.isAnnotationDeclaration()) {
            kind = TypeKind.ANNOTATION;
        }
        
        Set<Modifier> modifiers = extractModifiers(decl);
        List<com.vidnyan.ate.model.Annotation> typeAnnotations = extractAnnotations(decl.getAnnotations(), filePath);
        
        // Extract super types
        List<TypeRef> superTypes = new ArrayList<>();
        if (decl.getExtendedTypes().isNonEmpty()) {
            decl.getExtendedTypes().forEach(et -> 
                superTypes.add(TypeRef.of(et.getNameAsString())));
        }
        if (decl.getImplementedTypes().isNonEmpty()) {
            decl.getImplementedTypes().forEach(it -> 
                superTypes.add(TypeRef.of(it.getNameAsString())));
        }
        
        // Extract methods
        List<Method> typeMethods = new ArrayList<>();
        for (MethodDeclaration methodDecl : decl.getMethods()) {
            Method method = extractMethod(methodDecl, fqn, filePath);
            typeMethods.add(method);
            methods.put(method.getFullyQualifiedName(), method);
        }
        
        // Extract fields
        List<Field> typeFields = new ArrayList<>();
        for (FieldDeclaration fieldDecl : decl.getFields()) {
            for (VariableDeclarator var : fieldDecl.getVariables()) {
                Field field = extractField(var, fieldDecl, fqn, filePath);
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
    private Type extractEnum(EnumDeclaration decl, String packageName, java.nio.file.Path filePath) {
        String simpleName = decl.getNameAsString();
        String fqn = packageName.isEmpty() ? simpleName : packageName + "." + simpleName;
        
        Set<Modifier> modifiers = extractModifiers(decl);
        List<com.vidnyan.ate.model.Annotation> typeAnnotations = extractAnnotations(decl.getAnnotations(), filePath);
        
        // Enums can have methods and fields too
        List<Method> typeMethods = new ArrayList<>();
        for (MethodDeclaration methodDecl : decl.getMethods()) {
            Method method = extractMethod(methodDecl, fqn, filePath);
            typeMethods.add(method);
            methods.put(method.getFullyQualifiedName(), method);
        }
        
        List<Field> typeFields = new ArrayList<>();
        for (FieldDeclaration fieldDecl : decl.getFields()) {
            for (VariableDeclarator var : fieldDecl.getVariables()) {
                Field field = extractField(var, fieldDecl, fqn, filePath);
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
                        .line(decl.getBegin().map(p -> p.line).orElse(0))
                        .column(decl.getBegin().map(p -> p.column).orElse(0))
                        .build())
                .isSpringComponent(false)
                .isSpringConfiguration(false)
                .build();
    }
    
    /**
     * Extract a Method from a MethodDeclaration.
     */
    private Method extractMethod(MethodDeclaration decl, String containingTypeFqn, java.nio.file.Path filePath) {
        String methodName = decl.getNameAsString();
        String signature = buildMethodSignature(decl);
        String fqn = containingTypeFqn + "#" + signature;
        
        TypeRef returnType = TypeRef.of(decl.getType().map(t -> t.asString()).orElse("void"));
        
        List<Parameter> parameters = decl.getParameters().stream()
                .map(p -> Parameter.builder()
                        .name(p.getNameAsString())
                        .type(TypeRef.of(p.getType().asString()))
                        .isVarArgs(p.isVarArgs())
                        .location(Location.builder()
                                .filePath(filePath.toString())
                                .line(p.getBegin().map(b -> b.line).orElse(0))
                                .column(p.getBegin().map(b -> b.column).orElse(0))
                                .build())
                        .build())
                .toList();
        
        Set<Modifier> modifiers = extractModifiers(decl);
        List<com.vidnyan.ate.model.Annotation> methodAnnotations = extractAnnotations(decl.getAnnotations(), filePath);
        
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
                              String containingTypeFqn, java.nio.file.Path filePath) {
        String fieldName = var.getNameAsString();
        String fqn = containingTypeFqn + "." + fieldName;
        
        TypeRef type = TypeRef.of(fieldDecl.getElementType().asString());
        Set<Modifier> modifiers = extractModifiers(fieldDecl);
        List<com.vidnyan.ate.model.Annotation> fieldAnnotations = extractAnnotations(fieldDecl.getAnnotations(), filePath);
        
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
     * Extract modifiers from a node.
     */
    private Set<Modifier> extractModifiers(com.github.javaparser.ast.body.BodyDeclaration<?> decl) {
        Set<Modifier> modifiers = new HashSet<>();
        if (decl.isPublic()) modifiers.add(Modifier.PUBLIC);
        if (decl.isPrivate()) modifiers.add(Modifier.PRIVATE);
        if (decl.isProtected()) modifiers.add(Modifier.PROTECTED);
        if (decl.isStatic()) modifiers.add(Modifier.STATIC);
        if (decl.isFinal()) modifiers.add(Modifier.FINAL);
        if (decl.isAbstract()) modifiers.add(Modifier.ABSTRACT);
        return modifiers;
    }
    
    /**
     * Extract annotations from annotation expressions.
     */
    private List<com.vidnyan.ate.model.Annotation> extractAnnotations(
            List<AnnotationExpr> annotationExprs, java.nio.file.Path filePath) {
        return annotationExprs.stream()
                .map(ae -> {
                    String name = ae.getNameAsString();
                    // Try to resolve FQN (simplified - would need symbol solver for full resolution)
                    String fqn = name.contains(".") ? name : name; // TODO: Resolve via imports
                    
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
     * Resolve relationships between entities.
     * TODO: Extract method calls, field accesses, etc.
     */
    private void resolveRelationships() {
        // For now, create CONTAINS relationships
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

