package com.vidnyan.ate.adapter.out.parser;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.*;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import com.vidnyan.ate.application.port.out.SourceCodeParser;
import com.vidnyan.ate.domain.graph.CallEdge;
import com.vidnyan.ate.domain.model.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * JavaParser-based implementation of SourceCodeParser.
 * Parses Java source files and builds the domain model.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JavaParserAdapter implements SourceCodeParser {
    
    private final TypeResolverImpl typeResolver;
    
    @Override
    public ParsingResult parse(Path sourcePath, ParsingOptions options) {
        long startTime = System.currentTimeMillis();
        
        log.info("Parsing source code from: {}", sourcePath);
        
        // Configure JavaParser
        ParserConfiguration config = new ParserConfiguration()
                .setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_21);
        JavaParser parser = new JavaParser(config);
        
        // Collect all Java files
        List<Path> javaFiles = collectJavaFiles(sourcePath, options);
        log.info("Found {} Java files", javaFiles.size());
        
        // Parse all files
        Map<String, TypeEntity> types = new ConcurrentHashMap<>();
        Map<String, MethodEntity> methods = new ConcurrentHashMap<>();
        Map<String, FieldEntity> fields = new ConcurrentHashMap<>();
        List<Relationship> relationships = Collections.synchronizedList(new ArrayList<>());
        List<CallEdge> callEdges = Collections.synchronizedList(new ArrayList<>());
        
        AtomicInteger filesProcessed = new AtomicInteger(0);
        
        // Parse files (can be parallelized)
        javaFiles.parallelStream().forEach(file -> {
            try {
                parseFile(parser, file, types, methods, fields, relationships, callEdges);
                filesProcessed.incrementAndGet();
            } catch (Exception e) {
                log.warn("Failed to parse {}: {}", file, e.getMessage());
            }
        });
        
        // Build source model
        SourceModel sourceModel = SourceModel.builder()
                .types(Map.copyOf(types))
                .methods(Map.copyOf(methods))
                .fields(Map.copyOf(fields))
                .relationships(List.copyOf(relationships))
                .build();
        
        long duration = System.currentTimeMillis() - startTime;
        
        ParsingStats stats = new ParsingStats(
                filesProcessed.get(),
                types.size(),
                methods.size(),
                fields.size(),
                callEdges.size(),
                duration
        );
        
        log.info("Parsing complete: {} files, {} types, {} methods, {} calls in {}ms",
                stats.filesProcessed(), stats.typesExtracted(), 
                stats.methodsExtracted(), stats.callsExtracted(), stats.durationMs());
        
        return new ParsingResult(sourceModel, List.copyOf(callEdges), stats);
    }
    
    private List<Path> collectJavaFiles(Path sourcePath, ParsingOptions options) {
        try (Stream<Path> paths = Files.walk(sourcePath)) {
            return paths
                    .filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".java"))
                    .filter(p -> options.includeTests() || !isTestFile(p))
                    .filter(p -> !matchesExcludePattern(p, options.excludePatterns()))
                    .toList();
        } catch (IOException e) {
            log.error("Failed to walk directory: {}", sourcePath, e);
            return List.of();
        }
    }
    
    private boolean isTestFile(Path path) {
        String pathStr = path.toString();
        return pathStr.contains("/test/") || pathStr.contains("\\test\\") 
                || pathStr.endsWith("Test.java") || pathStr.endsWith("Tests.java");
    }
    
    private boolean matchesExcludePattern(Path path, List<String> patterns) {
        String pathStr = path.toString();
        return patterns.stream().anyMatch(pathStr::contains);
    }
    
    private void parseFile(
            JavaParser parser,
            Path file,
            Map<String, TypeEntity> types,
            Map<String, MethodEntity> methods,
            Map<String, FieldEntity> fields,
            List<Relationship> relationships,
            List<CallEdge> callEdges
    ) throws IOException {
        ParseResult<CompilationUnit> result = parser.parse(file);
        
        if (!result.isSuccessful() || result.getResult().isEmpty()) {
            return;
        }
        
        CompilationUnit cu = result.getResult().get();
        String packageName = cu.getPackageDeclaration()
                .map(pd -> pd.getNameAsString())
                .orElse("");
        
        // Build import map for type resolution
        Map<String, String> imports = buildImportMap(cu);
        
        // Process all type declarations
        cu.findAll(TypeDeclaration.class).forEach(td -> {
            processTypeDeclaration(td, packageName, file.toString(), imports,
                    types, methods, fields, relationships, callEdges);
        });
    }
    
    private Map<String, String> buildImportMap(CompilationUnit cu) {
        Map<String, String> imports = new HashMap<>();
        cu.getImports().forEach(imp -> {
            if (!imp.isAsterisk()) {
                String fqn = imp.getNameAsString();
                String simple = fqn.substring(fqn.lastIndexOf('.') + 1);
                imports.put(simple, fqn);
            }
        });
        return imports;
    }
    
    private void processTypeDeclaration(
            TypeDeclaration<?> td,
            String packageName,
            String filePath,
            Map<String, String> imports,
            Map<String, TypeEntity> types,
            Map<String, MethodEntity> methods,
            Map<String, FieldEntity> fields,
            List<Relationship> relationships,
            List<CallEdge> callEdges
    ) {
        String typeFqn = packageName.isEmpty() ? td.getNameAsString() 
                : packageName + "." + td.getNameAsString();
        
        // Determine type kind
        TypeEntity.TypeKind kind = determineTypeKind(td);
        
        // Extract modifiers
        Set<TypeEntity.Modifier> modifiers = extractModifiers(td);
        
        // Extract annotations
        List<AnnotationRef> annotations = extractAnnotations(td.getAnnotations());
        
        // Extract supertypes
        List<TypeRef> supertypes = new ArrayList<>();
        List<TypeRef> interfaces = new ArrayList<>();
        
        if (td instanceof ClassOrInterfaceDeclaration cid) {
            cid.getExtendedTypes().forEach(ext -> 
                supertypes.add(resolveTypeRef(ext.getNameAsString(), imports, packageName)));
            cid.getImplementedTypes().forEach(impl -> 
                interfaces.add(resolveTypeRef(impl.getNameAsString(), imports, packageName)));
        }
        
        // Create location
        Location location = Location.at(filePath, 
                td.getBegin().map(p -> p.line).orElse(0),
                td.getBegin().map(p -> p.column).orElse(0));
        
        // Create type entity
        TypeEntity typeEntity = new TypeEntity(
                typeFqn, td.getNameAsString(), packageName, kind,
                modifiers, annotations, supertypes, interfaces, location
        );
        types.put(typeFqn, typeEntity);
        
        // Process methods
        td.getMethods().forEach(method -> {
            processMethod(method, typeEntity, filePath, imports, methods, callEdges);
        });
        
        // Process fields
        td.getFields().forEach(field -> {
            processField(field, typeEntity, filePath, imports, fields);
        });
        
        // Process constructors
        if (td instanceof ClassOrInterfaceDeclaration cid) {
            cid.getConstructors().forEach(ctor -> {
                processConstructor(ctor, typeEntity, filePath, imports, methods, callEdges);
            });
        }
    }
    
    private void processMethod(
            MethodDeclaration md,
            TypeEntity containingType,
            String filePath,
            Map<String, String> imports,
            Map<String, MethodEntity> methods,
            List<CallEdge> callEdges
    ) {
        String methodFqn = containingType.fullyQualifiedName() + "#" + buildMethodSignature(md);
        
        // Extract parameters
        List<MethodEntity.Parameter> parameters = md.getParameters().stream()
                .map(p -> new MethodEntity.Parameter(
                        p.getNameAsString(),
                        resolveTypeRef(p.getTypeAsString(), imports, containingType.packageName()),
                        extractAnnotations(p.getAnnotations())
                ))
                .toList();
        
        // Create method entity
        MethodEntity methodEntity = new MethodEntity(
                methodFqn,
                md.getNameAsString(),
                containingType.fullyQualifiedName(),
                resolveTypeRef(md.getTypeAsString(), imports, containingType.packageName()),
                parameters,
                extractModifiers(md),
                extractAnnotations(md.getAnnotations()),
                md.getThrownExceptions().stream()
                        .map(t -> resolveTypeRef(t.asString(), imports, containingType.packageName()))
                        .toList(),
                Location.at(filePath, 
                        md.getBegin().map(p -> p.line).orElse(0),
                        md.getBegin().map(p -> p.column).orElse(0))
        );
        methods.put(methodFqn, methodEntity);
        
        // Extract method calls
        if (md.getBody().isPresent()) {
            extractMethodCalls(md, methodEntity, containingType, imports, callEdges);
        }
    }
    
    private void processConstructor(
            ConstructorDeclaration cd,
            TypeEntity containingType,
            String filePath,
            Map<String, String> imports,
            Map<String, MethodEntity> methods,
            List<CallEdge> callEdges
    ) {
        String ctorFqn = containingType.fullyQualifiedName() + "#<init>" + 
                buildParameterSignature(cd.getParameters());
        
        List<MethodEntity.Parameter> parameters = cd.getParameters().stream()
                .map(p -> new MethodEntity.Parameter(
                        p.getNameAsString(),
                        resolveTypeRef(p.getTypeAsString(), imports, containingType.packageName()),
                        extractAnnotations(p.getAnnotations())
                ))
                .toList();
        
        MethodEntity ctorEntity = new MethodEntity(
                ctorFqn,
                "<init>",
                containingType.fullyQualifiedName(),
                TypeRef.of(containingType.fullyQualifiedName()),
                parameters,
                extractModifiers(cd),
                extractAnnotations(cd.getAnnotations()),
                List.of(),
                Location.at(filePath,
                        cd.getBegin().map(p -> p.line).orElse(0),
                        cd.getBegin().map(p -> p.column).orElse(0))
        );
        methods.put(ctorFqn, ctorEntity);
    }
    
    private void processField(
            FieldDeclaration fd,
            TypeEntity containingType,
            String filePath,
            Map<String, String> imports,
            Map<String, FieldEntity> fields
    ) {
        String typeStr = fd.getElementType().asString();
        TypeRef typeRef = resolveTypeRef(typeStr, imports, containingType.packageName());
        
        fd.getVariables().forEach(var -> {
            String fieldFqn = containingType.fullyQualifiedName() + "#" + var.getNameAsString();
            
            FieldEntity fieldEntity = new FieldEntity(
                    fieldFqn,
                    var.getNameAsString(),
                    containingType.fullyQualifiedName(),
                    typeRef,
                    extractModifiers(fd),
                    extractAnnotations(fd.getAnnotations()),
                    Location.at(filePath,
                            var.getBegin().map(p -> p.line).orElse(0),
                            var.getBegin().map(p -> p.column).orElse(0))
            );
            fields.put(fieldFqn, fieldEntity);
        });
    }
    
    private void extractMethodCalls(
            MethodDeclaration md,
            MethodEntity method,
            TypeEntity containingType,
            Map<String, String> imports,
            List<CallEdge> callEdges
    ) {
        // Track local variable types
        Map<String, TypeRef> localVariables = new HashMap<>();
        
        // Track parameter types
        Map<String, TypeRef> parameterTypes = method.parameters().stream()
                .collect(Collectors.toMap(
                        MethodEntity.Parameter::name,
                        MethodEntity.Parameter::type
                ));
        
        md.getBody().ifPresent(body -> body.accept(new VoidVisitorAdapter<Void>() {
            
            @Override
            public void visit(VariableDeclarationExpr vd, Void arg) {
                super.visit(vd, arg);
                String typeStr = vd.getElementType().asString();
                TypeRef typeRef = resolveTypeRef(typeStr, imports, containingType.packageName());
                vd.getVariables().forEach(v -> localVariables.put(v.getNameAsString(), typeRef));
            }
            
            @Override
            public void visit(MethodCallExpr call, Void arg) {
                super.visit(call, arg);
                
                String calleeFqn = resolveMethodCall(call, containingType, imports, 
                        localVariables, parameterTypes);
                
                CallEdge edge = CallEdge.builder()
                        .caller(method.fullyQualifiedName())
                        .callee(buildRawCallSignature(call))
                        .resolvedCallee(calleeFqn)
                        .callType(determineCallType(call))
                        .location(Location.at(
                                md.findCompilationUnit()
                                        .flatMap(cu -> cu.getStorage())
                                        .map(s -> s.getPath().toString())
                                        .orElse("unknown"),
                                call.getBegin().map(p -> p.line).orElse(0),
                                call.getBegin().map(p -> p.column).orElse(0)
                        ))
                        .build();
                
                callEdges.add(edge);
            }
        }, null));
    }
    
    private String resolveMethodCall(
            MethodCallExpr call,
            TypeEntity containingType,
            Map<String, String> imports,
            Map<String, TypeRef> localVariables,
            Map<String, TypeRef> parameterTypes
    ) {
        String methodName = call.getNameAsString();
        String params = buildParameterPattern(call);
        
        if (!call.getScope().isPresent()) {
            // Implicit this call
            return containingType.fullyQualifiedName() + "#" + methodName + params;
        }
        
        String fullScope = call.getScope().get().toString();
        
        // Extract root variable from chained calls
        String scope = extractRootScope(fullScope);
        
        // 1. Check if scope is a field
        String fieldFqn = containingType.fullyQualifiedName() + "#" + scope;
        // We'd need access to fields here - for now use a simpler approach
        
        // 2. Check parameters
        if (parameterTypes.containsKey(scope)) {
            return parameterTypes.get(scope).fullyQualifiedName() + "#" + methodName + params;
        }
        
        // 3. Check local variables
        if (localVariables.containsKey(scope)) {
            return localVariables.get(scope).fullyQualifiedName() + "#" + methodName + params;
        }
        
        // 4. Check imports (static call)
        if (imports.containsKey(scope)) {
            return imports.get(scope) + "#" + methodName + params;
        }
        
        // 5. Check common JDK types
        String jdkType = resolveCommonJdkType(scope);
        if (jdkType != null) {
            return jdkType + "#" + methodName + params;
        }
        
        // 6. Lombok log
        if ("log".equals(scope)) {
            return "org.slf4j.Logger#" + methodName + params;
        }
        
        return null; // Unresolved
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
            case "Objects" -> "java.util.Objects";
            case "Files" -> "java.nio.file.Files";
            case "Paths" -> "java.nio.file.Paths";
            case "Path" -> "java.nio.file.Path";
            case "System" -> "java.lang.System";
            case "Math" -> "java.lang.Math";
            default -> null;
        };
    }
    
    // Helper methods
    
    private TypeEntity.TypeKind determineTypeKind(TypeDeclaration<?> td) {
        if (td instanceof EnumDeclaration) return TypeEntity.TypeKind.ENUM;
        if (td instanceof AnnotationDeclaration) return TypeEntity.TypeKind.ANNOTATION;
        if (td instanceof RecordDeclaration) return TypeEntity.TypeKind.RECORD;
        if (td instanceof ClassOrInterfaceDeclaration cid && cid.isInterface()) 
            return TypeEntity.TypeKind.INTERFACE;
        return TypeEntity.TypeKind.CLASS;
    }
    
    private Set<TypeEntity.Modifier> extractModifiers(com.github.javaparser.ast.nodeTypes.NodeWithModifiers<?> node) {
        Set<TypeEntity.Modifier> modifiers = EnumSet.noneOf(TypeEntity.Modifier.class);
        node.getModifiers().forEach(m -> {
            switch (m.getKeyword()) {
                case PUBLIC -> modifiers.add(TypeEntity.Modifier.PUBLIC);
                case PRIVATE -> modifiers.add(TypeEntity.Modifier.PRIVATE);
                case PROTECTED -> modifiers.add(TypeEntity.Modifier.PROTECTED);
                case STATIC -> modifiers.add(TypeEntity.Modifier.STATIC);
                case FINAL -> modifiers.add(TypeEntity.Modifier.FINAL);
                case ABSTRACT -> modifiers.add(TypeEntity.Modifier.ABSTRACT);
                case SYNCHRONIZED -> modifiers.add(TypeEntity.Modifier.SYNCHRONIZED);
                case VOLATILE -> modifiers.add(TypeEntity.Modifier.VOLATILE);
                case TRANSIENT -> modifiers.add(TypeEntity.Modifier.TRANSIENT);
                case NATIVE -> modifiers.add(TypeEntity.Modifier.NATIVE);
                case STRICTFP -> modifiers.add(TypeEntity.Modifier.STRICTFP);
                default -> {}
            }
        });
        return modifiers;
    }
    
    private List<AnnotationRef> extractAnnotations(
            com.github.javaparser.ast.NodeList<com.github.javaparser.ast.expr.AnnotationExpr> annotations
    ) {
        return annotations.stream()
                .map(a -> new AnnotationRef(
                        a.getNameAsString(),
                        a.getNameAsString(), // Would need imports to resolve FQN
                        extractAnnotationAttributes(a)
                ))
                .toList();
    }
    
    private Map<String, Object> extractAnnotationAttributes(AnnotationExpr annotation) {
        Map<String, Object> attributes = new HashMap<>();
        if (annotation instanceof SingleMemberAnnotationExpr single) {
            attributes.put("value", single.getMemberValue().toString());
        } else if (annotation instanceof NormalAnnotationExpr normal) {
            normal.getPairs().forEach(pair -> 
                attributes.put(pair.getNameAsString(), pair.getValue().toString()));
        }
        return attributes;
    }
    
    private TypeRef resolveTypeRef(String typeStr, Map<String, String> imports, String packageName) {
        // Handle primitives
        if (isPrimitive(typeStr)) {
            return TypeRef.primitive(typeStr);
        }
        
        // Handle arrays
        boolean isArray = typeStr.endsWith("[]");
        String baseType = isArray ? typeStr.substring(0, typeStr.length() - 2) : typeStr;
        
        // Handle generics (simplified)
        boolean isGeneric = baseType.contains("<");
        String simpleType = isGeneric ? baseType.substring(0, baseType.indexOf('<')) : baseType;
        
        // Resolve FQN
        String fqn;
        if (imports.containsKey(simpleType)) {
            fqn = imports.get(simpleType);
        } else if (simpleType.contains(".")) {
            fqn = simpleType; // Already FQN
        } else {
            // Assume same package or java.lang
            fqn = resolveCommonJdkType(simpleType);
            if (fqn == null) {
                fqn = packageName.isEmpty() ? simpleType : packageName + "." + simpleType;
            }
        }
        
        return new TypeRef(simpleType, fqn, false, isArray, isGeneric, List.of());
    }
    
    private boolean isPrimitive(String type) {
        return switch (type) {
            case "boolean", "byte", "char", "short", "int", "long", "float", "double", "void" -> true;
            default -> false;
        };
    }
    
    private String buildMethodSignature(MethodDeclaration md) {
        return md.getNameAsString() + buildParameterSignature(md.getParameters());
    }
    
    private String buildParameterSignature(
            com.github.javaparser.ast.NodeList<com.github.javaparser.ast.body.Parameter> params
    ) {
        String paramStr = params.stream()
                .map(p -> p.getTypeAsString())
                .collect(Collectors.joining(","));
        return "(" + paramStr + ")";
    }
    
    private String buildRawCallSignature(MethodCallExpr call) {
        String scope = call.getScope().map(s -> s.toString() + ".").orElse("");
        String args = call.getArguments().stream()
                .map(a -> "?")
                .collect(Collectors.joining(","));
        return scope + call.getNameAsString() + "(" + args + ")";
    }
    
    private String buildParameterPattern(MethodCallExpr call) {
        String args = call.getArguments().stream()
                .map(a -> "?")
                .collect(Collectors.joining(","));
        return "(" + args + ")";
    }
    
    private CallEdge.CallType determineCallType(MethodCallExpr call) {
        if (!call.getScope().isPresent()) {
            return CallEdge.CallType.DIRECT;
        }
        // More sophisticated analysis would be needed for accurate call type
        return CallEdge.CallType.VIRTUAL;
    }
}
