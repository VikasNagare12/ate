package com.vidnyan.ate.parser;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.resolution.TypeSolver;
import com.github.javaparser.symbolsolver.JavaSymbolSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.CombinedTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.ReflectionTypeSolver;
import lombok.Builder;
import lombok.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Parses Java source files into ASTs using JavaParser.
 * Extracts raw facts from ASTs.
 */
@Component
public class AstParser {
    
    private final JavaParser javaParser;
    
    public AstParser() {
        // Setup type solver for symbol resolution
        TypeSolver typeSolver = new CombinedTypeSolver(new ReflectionTypeSolver());
        
        // Configure parser with symbol resolver
        ParserConfiguration config = new ParserConfiguration();
        config.setSymbolResolver(new JavaSymbolSolver(typeSolver));
        
        this.javaParser = new JavaParser(config);
    }
    
    /**
     * Parse a single Java file into an AST.
     */
    public ParseResult parseFile(Path filePath) {
        try {
            String sourceCode = Files.readString(filePath);
            var parseResult = javaParser.parse(sourceCode);
            
            if (parseResult.isSuccessful() && parseResult.getResult().isPresent()) {
                CompilationUnit cu = parseResult.getResult().get();
                return ParseResult.builder()
                        .filePath(filePath)
                        .compilationUnit(cu)
                        .success(true)
                        .build();
            } else {
                return ParseResult.builder()
                        .filePath(filePath)
                        .success(false)
                        .error("Parse failed: " + parseResult.getProblems())
                        .build();
            }
        } catch (IOException e) {
            return ParseResult.builder()
                    .filePath(filePath)
                    .success(false)
                    .error(e.getMessage())
                    .build();
        } catch (Exception e) {
            return ParseResult.builder()
                    .filePath(filePath)
                    .success(false)
                    .error("Parse error: " + e.getMessage())
                    .build();
        }
    }
    
    /**
     * Parse multiple files.
     */
    public List<ParseResult> parseFiles(List<Path> filePaths) {
        List<ParseResult> results = new ArrayList<>();
        for (Path filePath : filePaths) {
            results.add(parseFile(filePath));
        }
        return results;
    }
    
    @Value
    @Builder
    public static class ParseResult {
        Path filePath;
        CompilationUnit compilationUnit;
        boolean success;
        String error;
    }
}

