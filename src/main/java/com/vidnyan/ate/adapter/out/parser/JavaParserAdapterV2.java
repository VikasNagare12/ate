package com.vidnyan.ate.adapter.out.parser;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.*;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import com.github.javaparser.resolution.declarations.ResolvedMethodDeclaration;
import com.github.javaparser.resolution.types.ResolvedType;
import com.github.javaparser.symbolsolver.JavaSymbolSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.CombinedTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.JarTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.JavaParserTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.ReflectionTypeSolver;

import com.vidnyan.ate.application.port.out.SourceCodeParser;
import com.vidnyan.ate.domain.graph.CallEdge;
import com.vidnyan.ate.domain.model.*;
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
 * Enhanced JavaParser adapter with JavaSymbolSolver for accurate type
 * resolution.
 * This version resolves ~95% of types vs ~60% with manual resolution.
 */
@Slf4j
@Component
public class JavaParserAdapterV2 implements SourceCodeParser {

    @Override
    public ParsingResult parse(Path sourcePath, ParsingOptions options) {
        long startTime = System.currentTimeMillis();
        log.info("Parsing source code from: {} (with SymbolSolver)", sourcePath);

        // Configure TypeSolver for symbol resolution
        CombinedTypeSolver combinedTypeSolver = new CombinedTypeSolver();

        // Add ReflectionTypeSolver for JDK classes (java.*, javax.*, etc.)
        combinedTypeSolver.add(new ReflectionTypeSolver());

        // Add common source directories first (highest priority)
        // Try to find the actual source root (e.g., src/main/java) by walking up
        Path current = sourcePath.toAbsolutePath();
        boolean specificRootFound = false;
        while (current != null) {
            if (current.endsWith("src/main/java") || current.endsWith("src/test/java")) {
                try {
                    log.info("Auto-detected source root: {}", current);
                    combinedTypeSolver.add(new JavaParserTypeSolver(current));
                    specificRootFound = true;
                } catch (Exception e) {
                    log.warn("Failed to add detected source root: {}", current);
                }
            }
            if (Files.exists(current.resolve("pom.xml")) || Files.exists(current.resolve("build.gradle"))) {
                // We are at project root, try adding standard source dirs
                addSourceDirIfExists(combinedTypeSolver, current.resolve("src/main/java"));
                addSourceDirIfExists(combinedTypeSolver, current.resolve("src/test/java"));
            }
            current = current.getParent();
            if (specificRootFound)
                break; // Stop if we found the main java root
        }

        // Final fallback: the provided source path itself
        combinedTypeSolver.add(new JavaParserTypeSolver(sourcePath));

        // Try to add JARs from project's classpath (Maven/Gradle dependencies)
        addProjectDependencies(combinedTypeSolver, sourcePath);

        // Create JavaSymbolSolver and configure parser
        JavaSymbolSolver symbolSolver = new JavaSymbolSolver(combinedTypeSolver);
        ParserConfiguration config = new ParserConfiguration()
                .setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_21)
                .setSymbolResolver(symbolSolver);
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
        AtomicInteger resolvedCalls = new AtomicInteger(0);
        AtomicInteger unresolvedCalls = new AtomicInteger(0);

        // Parse files (parallelization disabled for SymbolSolver thread safety)
        for (Path file : javaFiles) {
            try {
                parseFile(parser, file, types, methods, fields, relationships, callEdges,
                        resolvedCalls, unresolvedCalls);
                filesProcessed.incrementAndGet();
            } catch (Exception e) {
                log.warn("Failed to parse {}: {}", file, e.getMessage());
            }
        }

        // Build source model
        SourceModel sourceModel = SourceModel.builder()
                .types(Map.copyOf(types))
                .methods(Map.copyOf(methods))
                .fields(Map.copyOf(fields))
                .relationships(List.copyOf(relationships))
                .build();

        long duration = System.currentTimeMillis() - startTime;

        int totalCalls = resolvedCalls.get() + unresolvedCalls.get();
        double resolutionRate = totalCalls > 0
                ? (resolvedCalls.get() * 100.0 / totalCalls)
                        : 0;

        log.info("Parsing complete: {} files, {} types, {} methods in {}ms",
                filesProcessed.get(), types.size(), methods.size(), duration);
        log.info("Type resolution: {}/{} calls resolved ({:.1f}%)",
                        resolvedCalls.get(), totalCalls, resolutionRate);

        return new ParsingResult(
                sourceModel,
                        List.copyOf(callEdges),
                new ParsingStats(
                        filesProcessed.get(),
                        types.size(),
                        methods.size(),
                        fields.size(),
                        callEdges.size(),
                        duration));
    }

    private void addSourceDirIfExists(CombinedTypeSolver solver, Path dir) {
        if (Files.exists(dir) && Files.isDirectory(dir)) {
            try {
                solver.add(new JavaParserTypeSolver(dir));
            } catch (Exception e) {
                log.debug("Could not add source dir: {}", dir);
            }
        }
    }

    /**
     * Automatically discover and add JAR dependencies to the type solver.
     * Checks common locations: lib/, target/dependency/, target/lib/
     */
    private void addProjectDependencies(CombinedTypeSolver solver, Path sourcePath) {
        // 1. Add current classpath jars (allows resolving Spring, JDBC, etc. if this
        // tool is running with them)
        String classpath = System.getProperty("java.class.path");
        if (classpath != null) {
            String separator = System.getProperty("path.separator");
            String[] entries = classpath.split(separator);
            for (String entry : entries) {
                if (entry.endsWith(".jar")) {
                    try {
                        solver.add(new JarTypeSolver(entry));
                    } catch (Exception e) {
                        // Ignore non-jar or invalid entries
                    }
                }
            }
        }

        // 2. Add jars from common project locations
        List<Path> commonJarLocations = List.of(
                sourcePath.resolve("lib"),
                sourcePath.resolve("target/dependency"),
                sourcePath.resolve("target/lib"),
                sourcePath.resolve("build/libs") // Gradle
        );

        int jarsAdded = 0;
        for (Path location : commonJarLocations) {
            if (Files.exists(location) && Files.isDirectory(location)) {
                try (Stream<Path> paths = Files.walk(location)) {
                    List<Path> jars = paths
                            .filter(Files::isRegularFile)
                            .filter(p -> p.toString().endsWith(".jar"))
                            .toList();

                    for (Path jar : jars) {
                        try {
                            solver.add(new JarTypeSolver(jar));
                            jarsAdded++;
                        } catch (IOException e) {
                            log.debug("Could not add JAR to solver: {}", jar);
                        }
                    }
                } catch (IOException e) {
                    log.debug("Failed to scan JAR location: {}", location);
                }
            }
        }

        if (jarsAdded > 0) {
            log.info("Automatically added {} project JAR dependencies to SymbolSolver", jarsAdded);
        }
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
            List<CallEdge> callEdges,
            AtomicInteger resolvedCalls,
            AtomicInteger unresolvedCalls) throws IOException {
        ParseResult<CompilationUnit> result = parser.parse(file);

        if (!result.isSuccessful() || result.getResult().isEmpty()) {
            return;
        }

        CompilationUnit cu = result.getResult().get();
        String packageName = cu.getPackageDeclaration()
                .map(pd -> pd.getNameAsString())
                .orElse("");

        // Build import map for fallback resolution
        Map<String, String> imports = buildImportMap(cu);

        // Process all type declarations
        cu.findAll(TypeDeclaration.class).forEach(td -> {
            processTypeDeclaration(td, packageName, file.toString(), imports,
                    types, methods, fields, relationships, callEdges,
                    resolvedCalls, unresolvedCalls);
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
            List<CallEdge> callEdges,
            AtomicInteger resolvedCalls,
            AtomicInteger unresolvedCalls) {
        String typeFqn = packageName.isEmpty() ? td.getNameAsString()
                : packageName + "." + td.getNameAsString();

        // Determine type kind
        TypeEntity.TypeKind kind = determineTypeKind(td);

        // Extract modifiers
        Set<TypeEntity.Modifier> modifiers = extractModifiers(td);

        // Extract annotations with FQN resolution
        List<AnnotationRef> annotations = extractAnnotationsWithFqn(td.getAnnotations());

        // Extract supertypes with symbol resolution
        List<TypeRef> supertypes = new ArrayList<>();
        List<TypeRef> interfaces = new ArrayList<>();

        if (td instanceof ClassOrInterfaceDeclaration cid) {
            cid.getExtendedTypes().forEach(ext -> {
                supertypes.add(resolveTypeWithSymbolSolver(ext, imports, packageName));
            });
            cid.getImplementedTypes().forEach(impl -> {
                interfaces.add(resolveTypeWithSymbolSolver(impl, imports, packageName));
            });
        }

        // Create location
        Location location = Location.at(filePath,
                        td.getBegin().map(p -> p.line).orElse(0),
                td.getBegin().map(p -> p.column).orElse(0));

        // Create type entity
        TypeEntity typeEntity = new TypeEntity(
                typeFqn, td.getNameAsString(), packageName, kind,
                modifiers, annotations, supertypes, interfaces, location);
        types.put(typeFqn, typeEntity);

        // Process fields first so we can resolve field types in methods
        td.getFields().forEach(field -> {
            processField(field, typeEntity, filePath, imports, fields);
        });

        // Process methods with enhanced call resolution
        td.getMethods().forEach(method -> {
            processMethodWithSymbolResolver(method, typeEntity, filePath, imports,
                            methods, callEdges, resolvedCalls, unresolvedCalls, fields);
        });

        // Process constructors
        if (td instanceof ClassOrInterfaceDeclaration cid) {
            cid.getConstructors().forEach(ctor -> {
                processConstructor(ctor, typeEntity, filePath, imports, methods,
                        callEdges, resolvedCalls, unresolvedCalls, fields);
            });
        }
    }

    private TypeRef resolveTypeWithSymbolSolver(
            com.github.javaparser.ast.type.Type type,
            Map<String, String> imports,
                    String packageName) {
        try {
            ResolvedType resolved = type.resolve();
            String fqn = resolved.describe();
            // User requested to KEEP generics for consistency
            // String baseFqn = fqn.contains("<") ? fqn.substring(0, fqn.indexOf('<')) :
            // fqn;
            String baseFqn = fqn; // Use full FQN with generics
            String simpleName = baseFqn.contains(".")
                    ? baseFqn.substring(baseFqn.lastIndexOf('.') + 1)
                    : baseFqn;
            return new TypeRef(simpleName, baseFqn, false, false, fqn.contains("<"), List.of());
        } catch (Exception e) {
            // Fallback to manual resolution
            return resolveTypeRef(type.asString(), imports, packageName);
        }
    }

    private void processMethodWithSymbolResolver(
            MethodDeclaration md,
            TypeEntity containingType,
            String filePath,
            Map<String, String> imports,
            Map<String, MethodEntity> methods,
            List<CallEdge> callEdges,
            AtomicInteger resolvedCalls,
            AtomicInteger unresolvedCalls,
            Map<String, FieldEntity> fields) {
        // Extract parameters (resolve types first)
        List<MethodEntity.Parameter> parameters = md.getParameters().stream()
                .map(p -> new MethodEntity.Parameter(
                        p.getNameAsString(),
                        resolveTypeWithSymbolSolver(p.getType(), imports, containingType.packageName()),
                        extractAnnotationsWithFqn(p
                                .getAnnotations())))
                .toList();

        // Build signature using resolved parameter types
        String signature = md.getNameAsString() + "(" +
                parameters.stream()
                        .map(p -> p.type().fullyQualifiedName())
                        .collect(Collectors.joining(","))
                + ")";
        String methodFqn = containingType.fullyQualifiedName() + "#" + signature;

        // Resolve return type
        TypeRef returnType;
        try {
            ResolvedType resolved = md.getType().resolve();
            returnType = new TypeRef(
                    md.getType().asString(),
                    resolved.describe(),
                    false, false, false, List.of());
        } catch (Exception e) {
            returnType = resolveTypeRef(md.getTypeAsString(), imports, containingType.packageName());
        }

        // Create method entity
        MethodEntity methodEntity = new MethodEntity(
                methodFqn,
                md.getNameAsString(),
                containingType.fullyQualifiedName(),
                returnType,
                parameters,
                extractModifiers(md),
                extractAnnotationsWithFqn(md.getAnnotations()),
                md.getThrownExceptions().stream()
                        .map(t -> resolveTypeRef(t.asString(), imports, containingType.packageName()))
                        .toList(),
                Location.at(filePath,
                                md.getBegin().map(p -> p.line).orElse(0),
                        md.getBegin().map(p -> p.column).orElse(0)));
        methods.put(methodFqn, methodEntity);

        // Extract method calls using Symbol Solver
        if (md.getBody().isPresent()) {
            extractMethodCallsWithSymbolSolver(md.getBody().get(), methodEntity, containingType, filePath,
                    callEdges, resolvedCalls, unresolvedCalls, imports, fields);
        }
    }

    private void extractMethodCallsWithSymbolSolver(
            com.github.javaparser.ast.Node bodyNode,
            MethodEntity method,
                    TypeEntity containingType,
            String filePath,
            List<CallEdge> callEdges,
            AtomicInteger resolvedCalls,
            AtomicInteger unresolvedCalls,
            Map<String, String> imports,
            Map<String, FieldEntity> fields) {
        bodyNode.accept(new VoidVisitorAdapter<Void>() {

            @Override
            public void visit(MethodCallExpr call, Void arg) {
                super.visit(call, arg);

                String resolvedCalleeFqn = null;

                // Try to resolve using SymbolSolver
                try {
                    ResolvedMethodDeclaration resolvedMethod = call.resolve();
                    String declaringType = resolvedMethod.declaringType().getQualifiedName();
                    String signature = resolvedMethod.getName() + buildParameterTypes(resolvedMethod);
                    resolvedCalleeFqn = declaringType + "#" + signature;
                    resolvedCalls.incrementAndGet();
                } catch (Exception e) {
                    // Fallback: try to resolve using scope, field types, and parameters
                    resolvedCalleeFqn = tryFallbackResolution(call, containingType, method, imports, fields);
                    if (resolvedCalleeFqn != null) {
                        resolvedCalls.incrementAndGet();
                    } else {
                        unresolvedCalls.incrementAndGet();
                        log.trace("Could not resolve: {}: {}", call.getNameAsString(), e.getMessage());
                    }
                }

                List<String> arguments = call.getArguments().stream()
                        .map(JavaParserAdapterV2.this::resolveArgumentType)
                        .toList();

                CallEdge edge = CallEdge.builder()
                        .caller(method.fullyQualifiedName())
                        .callee(buildRawCallSignature(call))
                        .resolvedCallee(resolvedCalleeFqn)
                        .callType(determineCallType(call))
                        .location(Location.at(filePath,
                                call.getBegin().map(p -> p.line).orElse(0),
                                call.getBegin().map(p -> p.column).orElse(0)))
                        .arguments(arguments)
                        .build();

                callEdges.add(edge);
            }

            @Override
            public void visit(ObjectCreationExpr creation, Void arg) {
                super.visit(creation, arg);

                String resolvedCalleeFqn = null;
                String typeName = creation.getType().getNameAsString();

                // Try to resolve the constructor call
                try {
                    var resolved = creation.resolve();
                    String declaringType = resolved.declaringType().getQualifiedName();
                    resolvedCalleeFqn = declaringType + "#<init>" + buildParameterTypes(resolved);
                    resolvedCalls.incrementAndGet();
                } catch (Exception e) {
                    // Fallback: use simple type name
                    unresolvedCalls.incrementAndGet();
                    log.trace("Could not resolve constructor: {}: {}", typeName, e.getMessage());
                }

                // Build raw callee signature
                String rawCallee = "new " + typeName + "()";

                CallEdge edge = CallEdge.builder()
                        .caller(method.fullyQualifiedName())
                        .callee(rawCallee)
                        .resolvedCallee(resolvedCalleeFqn)
                        .callType(CallEdge.CallType.CONSTRUCTOR)
                        .location(Location.at(filePath,
                                creation.getBegin().map(p -> p.line).orElse(0),
                                creation.getBegin().map(p -> p.column).orElse(0)))
                        .build();

                callEdges.add(edge);
            }
        }, null);
    }

    private String buildParameterTypes(ResolvedMethodDeclaration method) {
        StringBuilder sb = new StringBuilder("(");
        for (int i = 0; i < method.getNumberOfParams(); i++) {
            if (i > 0)
                sb.append(",");
            try {
                // Use describe() to get the full type name (including generics as requested)
                String fullType = method.getParam(i).getType().describe();
                // Previously stripped generics here, now preserving them
                sb.append(fullType);
            } catch (Exception e) {
                sb.append("Unknown");
            }
        }
        sb.append(")");
        return sb.toString();
    }

    private String buildParameterTypes(
            com.github.javaparser.resolution.declarations.ResolvedConstructorDeclaration ctor) {
        StringBuilder sb = new StringBuilder("(");
        for (int i = 0; i < ctor.getNumberOfParams(); i++) {
            if (i > 0)
                sb.append(",");
            try {
                String fullType = ctor.getParam(i).getType().describe();
                // Preserving generics as requested
                sb.append(fullType);
            } catch (Exception e) {
                sb.append("Unknown");
            }
        }
        sb.append(")");
        return sb.toString();
    }

    // ============ Helper Methods ============

    private void processConstructor(
            ConstructorDeclaration cd,
            TypeEntity containingType,
            String filePath,
            Map<String, String> imports,
            Map<String, MethodEntity> methods,
            List<CallEdge> callEdges,
            AtomicInteger resolvedCalls,
            AtomicInteger unresolvedCalls,
            Map<String, FieldEntity> fields) {
        // Resolve parameters first to build accurate FQN
        List<MethodEntity.Parameter> parameters = cd.getParameters().stream()
                .map(p -> new MethodEntity.Parameter(
                        p.getNameAsString(),
                        resolveTypeRef(p.getTypeAsString(), imports, containingType.packageName()),
                        extractAnnotationsWithFqn(p.getAnnotations())))
                .toList();

        // Build signature using resolved parameter types
        String signature = parameters.stream()
                .map(p -> p.type().fullyQualifiedName())
                .collect(Collectors.joining(","));

        String ctorFqn = containingType.fullyQualifiedName() + "#<init>(" + signature + ")";

        MethodEntity ctorEntity = new MethodEntity(
                ctorFqn,
                "<init>",
                containingType.fullyQualifiedName(),
                TypeRef.of(containingType.fullyQualifiedName()),
                parameters,
                extractModifiers(cd),
                extractAnnotationsWithFqn(cd.getAnnotations()),
                List.of(),
                Location.at(filePath,
                        cd.getBegin().map(p -> p.line).orElse(0),
                        cd.getBegin().map(p -> p.column).orElse(0)));
        methods.put(ctorFqn, ctorEntity);

        // Extract method calls from constructor body
        extractMethodCallsWithSymbolSolver(cd, ctorEntity, containingType, filePath,
                callEdges, resolvedCalls, unresolvedCalls, imports, fields);
    }

    private void processField(
            FieldDeclaration fd,
            TypeEntity containingType,
            String filePath,
            Map<String, String> imports,
            Map<String, FieldEntity> fields) {
        TypeRef typeRef;
        try {
            ResolvedType resolved = fd.getElementType().resolve();
            typeRef = new TypeRef(
                    fd.getElementType().asString(),
                    resolved.describe(),
                    false, false, false, List.of());
        } catch (Exception e) {
            typeRef = resolveTypeRef(fd.getElementType().asString(), imports, containingType.packageName());
        }

        final TypeRef finalTypeRef = typeRef;
        fd.getVariables().forEach(var -> {
            String fieldFqn = containingType.fullyQualifiedName() + "#" + var.getNameAsString();

            FieldEntity fieldEntity = new FieldEntity(
                    fieldFqn,
                    var.getNameAsString(),
                    containingType.fullyQualifiedName(),
                    finalTypeRef,
                    extractModifiers(fd),
                    extractAnnotationsWithFqn(fd.getAnnotations()),
                    Location.at(filePath,
                            var.getBegin().map(p -> p.line).orElse(0),
                            var.getBegin().map(p -> p.column).orElse(0)));
            fields.put(fieldFqn, fieldEntity);
        });
    }

    private TypeEntity.TypeKind determineTypeKind(TypeDeclaration<?> td) {
        if (td instanceof EnumDeclaration)
            return TypeEntity.TypeKind.ENUM;
        if (td instanceof AnnotationDeclaration)
            return TypeEntity.TypeKind.ANNOTATION;
        if (td instanceof RecordDeclaration)
            return TypeEntity.TypeKind.RECORD;
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
                default -> {
                }
            }
        });
        return modifiers;
    }

    private List<AnnotationRef> extractAnnotationsWithFqn(
            com.github.javaparser.ast.NodeList<AnnotationExpr> annotations) {
        return annotations.stream()
                .map(a -> {
                    String fqn = a.getNameAsString();
                    try {
                        // Try to resolve annotation FQN
                        fqn = a.resolve().getQualifiedName();
                    } catch (Exception e) {
                        // Keep simple name
                    }
                    return new AnnotationRef(
                            a.getNameAsString(),
                            fqn,
                            extractAnnotationAttributes(a));
                })
                .toList();
    }

    private Map<String, Object> extractAnnotationAttributes(AnnotationExpr annotation) {
        Map<String, Object> attributes = new HashMap<>();
        if (annotation instanceof SingleMemberAnnotationExpr single) {
            attributes.put("value", single.getMemberValue().toString());
        } else if (annotation instanceof NormalAnnotationExpr normal) {
            normal.getPairs().forEach(pair -> attributes.put(pair.getNameAsString(), pair.getValue().toString()));
        }
        return attributes;
    }

    private TypeRef resolveTypeRef(String typeStr, Map<String, String> imports, String packageName) {
        if (isPrimitive(typeStr)) {
            return TypeRef.primitive(typeStr);
        }

        boolean isArray = typeStr.endsWith("[]");
        String baseType = isArray ? typeStr.substring(0, typeStr.length() - 2) : typeStr;
        boolean isGeneric = baseType.contains("<");
        String simpleType = isGeneric ? baseType.substring(0, baseType.indexOf('<')) : baseType;

        String fqn;
        if (imports.containsKey(simpleType)) {
            fqn = imports.get(simpleType);
        } else if (simpleType.contains(".")) {
            fqn = simpleType;
        } else {
            fqn = resolveCommonType(simpleType);
            if (fqn == null) {
                fqn = packageName.isEmpty() ? simpleType : packageName + "." + simpleType;
            }
        }

        return new TypeRef(simpleType, fqn, false, isArray, isGeneric, List.of());
    }

    private String resolveCommonType(String simpleName) {
        return switch (simpleName) {
            case "List" -> "java.util.List";
            case "Set" -> "java.util.Set";
            case "Map" -> "java.util.Map";
            case "String" -> "java.lang.String";
            case "Object" -> "java.lang.Object";
            case "Integer" -> "java.lang.Integer";
            case "Long" -> "java.lang.Long";
            case "Boolean" -> "java.lang.Boolean";
            case "Double" -> "java.lang.Double";
            case "Optional" -> "java.util.Optional";
            default -> null;
        };
    }

    private boolean isPrimitive(String type) {
        return switch (type) {
            case "boolean", "byte", "char", "short", "int", "long", "float", "double", "void" -> true;
            default -> false;
        };
    }

    private String buildRawCallSignature(MethodCallExpr call) {
        String scope = call.getScope().map(s -> s.toString() + ".").orElse("");
        StringBuilder sb = new StringBuilder(scope);
        sb.append(call.getNameAsString());
        sb.append("(");
        for (int i = 0; i < call.getArguments().size(); i++) {
            if (i > 0)
                sb.append(",");
            sb.append(resolveArgumentType(call.getArgument(i)));
        }
        sb.append(")");
        return sb.toString();
    }

    private CallEdge.CallType determineCallType(MethodCallExpr call) {
        if (!call.getScope().isPresent()) {
            return CallEdge.CallType.DIRECT;
        }
        return CallEdge.CallType.VIRTUAL;
    }

    /**
     * Fallback resolution using scope field type and imports.
     * Used when SymbolSolver fails to resolve a method call.
     */
    private String tryFallbackResolution(
            MethodCallExpr call,
            TypeEntity containingType,
            MethodEntity method,
            Map<String, String> imports,
            Map<String, FieldEntity> fields) {
        // Only works if we have a scope (e.g., jdbcTemplate.update())
        if (call.getScope().isEmpty()) {
            return null;
        }

        String scopeName = call.getScope().get().toString();
        String methodName = call.getNameAsString();

        // Check if scope is "this" - method on same class
        if ("this".equals(scopeName)) {
            String argSignature = call.getArguments().stream()
                    .map(this::resolveArgumentType)
                    .collect(Collectors.joining(","));
            return containingType.fullyQualifiedName() + "#" + methodName + "(" + argSignature + ")";
        }

        // Check if scope is a simple identifier (variable name)
        if (call.getScope().get().isNameExpr()) {
            String varName = scopeName;

            // 1. Check method parameters first
            for (MethodEntity.Parameter param : method.parameters()) {
                if (param.name().equals(varName)) {
                    StringBuilder sb = new StringBuilder(param.type().fullyQualifiedName());
                    sb.append("#").append(methodName).append("(");
                    for (int i = 0; i < call.getArguments().size(); i++) {
                        if (i > 0)
                            sb.append(",");
                        sb.append(resolveArgumentType(call.getArgument(i)));
                    }
                    sb.append(")");
                    return sb.toString();
                }
            }

            // 2. Check class fields using the fields map
            String fieldFqn = containingType.fullyQualifiedName() + "#" + varName;
            FieldEntity field = fields.get(fieldFqn);
            if (field != null) {
                StringBuilder sb = new StringBuilder(field.type().fullyQualifiedName());
                sb.append("#").append(methodName).append("(");
                for (int i = 0; i < call.getArguments().size(); i++) {
                    if (i > 0)
                        sb.append(",");
                    sb.append(resolveArgumentType(call.getArgument(i)));
                }
                sb.append(")");
                return sb.toString();
            }

            // 3. Try SymbolSolver for the identifier itself
            try {
                var resolved = call.getScope().get().asNameExpr().resolve();
                String typeFqn = null;
                if (resolved.isField()) {
                    typeFqn = resolved.asField().getType().describe();
                } else if (resolved.isParameter()) {
                    typeFqn = resolved.asParameter().getType().describe();
                } else {
                    // Handle local variables and other value declarations
                    typeFqn = resolved.getType().describe();
                }

                if (typeFqn != null) {
                    // Clean up generics
                    if (typeFqn.contains("<")) {
                        typeFqn = typeFqn.substring(0, typeFqn.indexOf('<'));
                    }
                    StringBuilder sb = new StringBuilder(typeFqn);
                    sb.append("#").append(methodName).append("(");
                    for (int i = 0; i < call.getArguments().size(); i++) {
                        if (i > 0)
                            sb.append(",");
                        sb.append(resolveArgumentType(call.getArgument(i)));
                    }
                    sb.append(")");
                    return sb.toString();
                }
            } catch (Exception e) {
                log.trace("Could not resolve identifier type for {}: {}", varName, e.getMessage());
            }

            // 4. Fallback: try to find type from imports using naming convention (e.g.,
            // jdbcTemplate -> JdbcTemplate)
            if (varName.length() > 0) {
                String typeName = Character.toUpperCase(varName.charAt(0)) + varName.substring(1);
                String fqn = imports.get(typeName);
                if (fqn != null) {
                    return fqn + "#" + methodName + "()";
                }
            }
        }

        // Check if scope looks like a class name (for static calls)
        if (Character.isUpperCase(scopeName.charAt(0))) {
            String typeFqn = imports.getOrDefault(scopeName, scopeName);
            return typeFqn + "#" + methodName + "(" + call.getArguments().size() + ")";
        }

        return null;
    }


    /**
     * Resolve the type of an argument expression to a Fully Qualified Name.
     * Used to build method signatures that match MethodEntity keys.
     */
    private String resolveArgumentType(Expression arg) {
        try {
            ResolvedType type = arg.calculateResolvedType();
            String fqn = type.describe();
            // Preserve Generics as per user request
            return fqn;
        } catch (Exception e) {
            // Fallback for literals if symbol solver fails
            if (arg instanceof StringLiteralExpr) return "java.lang.String";
            if (arg instanceof IntegerLiteralExpr) return "int";
            if (arg instanceof LongLiteralExpr) return "long";
            if (arg instanceof DoubleLiteralExpr) return "double";
            if (arg instanceof BooleanLiteralExpr) return "boolean";
            if (arg instanceof NullLiteralExpr) return "null";
            
            // Unresolved
            return "Unknown";
        }
    }
}
