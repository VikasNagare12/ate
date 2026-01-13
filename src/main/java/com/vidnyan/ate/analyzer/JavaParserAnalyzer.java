package com.vidnyan.ate.analyzer;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.symbolsolver.JavaSymbolSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.CombinedTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.JavaParserTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.ReflectionTypeSolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Stream;

/**
 * JavaParser-based analyzer for source code parsing.
 * Provides: Full AST, annotations with values, generics, source code snippets.
 */
@Component
public class JavaParserAnalyzer {

    private static final Logger log = LoggerFactory.getLogger(JavaParserAnalyzer.class);

    /**
     * Parse all Java source files in a directory.
     */
    public SourceAnalysisResult analyze(Path sourcePath) {
        log.info("JavaParser: Analyzing source files from: {}", sourcePath);
        
        // Setup symbol solver
        CombinedTypeSolver typeSolver = new CombinedTypeSolver();
        typeSolver.add(new ReflectionTypeSolver());
        typeSolver.add(new JavaParserTypeSolver(sourcePath));
        
        JavaSymbolSolver symbolSolver = new JavaSymbolSolver(typeSolver);
        JavaParser parser = new JavaParser();
        parser.getParserConfiguration().setSymbolResolver(symbolSolver);
        
        Map<String, MethodInfo> methods = new HashMap<>();
        
        try (Stream<Path> paths = Files.walk(sourcePath)) {
            paths.filter(p -> p.toString().endsWith(".java"))
                .forEach(file -> parseFile(parser, file, methods));
        } catch (IOException e) {
            log.error("Failed to walk source path", e);
        }
        
        log.info("JavaParser: Parsed {} methods", methods.size());
        return new SourceAnalysisResult(methods);
    }

    private void parseFile(JavaParser parser, Path file, Map<String, MethodInfo> methods) {
        try {
            ParseResult<CompilationUnit> result = parser.parse(file);
            if (result.isSuccessful() && result.getResult().isPresent()) {
                CompilationUnit cu = result.getResult().get();
                String packageName = cu.getPackageDeclaration()
                    .map(p -> p.getNameAsString())
                    .orElse("");
                
                cu.findAll(MethodDeclaration.class).forEach(method -> {
                    String className = method.findAncestor(com.github.javaparser.ast.body.ClassOrInterfaceDeclaration.class)
                        .map(c -> c.getNameAsString())
                        .orElse("Unknown");
                    
                    String fqn = packageName + "." + className + "." + method.getNameAsString();
                    
                    // Get annotations
                    List<String> annotations = method.getAnnotations().stream()
                        .map(a -> "@" + a.getNameAsString())
                        .toList();
                    
                    // Get method calls
                    List<String> calls = method.findAll(MethodCallExpr.class).stream()
                        .map(call -> {
                            try {
                                return call.resolve().getQualifiedName();
                            } catch (Exception e) {
                                return call.getNameAsString();
                            }
                        })
                        .toList();
                    
                    // Get source code
                    String sourceCode = method.toString();
                    int startLine = method.getBegin().map(p -> p.line).orElse(0);
                    int endLine = method.getEnd().map(p -> p.line).orElse(0);
                    
                    methods.put(fqn, new MethodInfo(
                        fqn,
                        className,
                        method.getNameAsString(),
                        annotations,
                        calls,
                        sourceCode,
                        file.toString(),
                        startLine,
                        endLine
                    ));
                });
            }
        } catch (IOException e) {
            log.warn("Failed to parse: {}", file, e);
        }
    }

    /**
     * Result of source analysis.
     */
    public record SourceAnalysisResult(Map<String, MethodInfo> methods) {
        public Optional<MethodInfo> getMethod(String fqn) {
            return Optional.ofNullable(methods.get(fqn));
        }
        
        public List<MethodInfo> getMethodsWithAnnotation(String annotationName) {
            return methods.values().stream()
                .filter(m -> m.annotations().stream()
                    .anyMatch(a -> a.contains(annotationName)))
                .toList();
        }
    }

    /**
     * Information about a method.
     */
    public record MethodInfo(
        String fullyQualifiedName,
        String className,
        String methodName,
        List<String> annotations,
        List<String> directCalls,
        String sourceCode,
        String filePath,
        int startLine,
        int endLine
    ) {}
}
