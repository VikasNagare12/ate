package com.vidnyan.ate.scanner;

import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * Scans repository for Java source files.
 * Discovers all .java files in the source roots.
 */
@Component
public class RepositoryScanner {
    
    private Path repositoryRoot;
    private final List<String> sourceRoots = new ArrayList<>();
    
    /**
     * Initialize scanner with repository root.
     */
    public void initialize(Path repositoryRoot) {
        this.repositoryRoot = repositoryRoot;
        this.sourceRoots.clear();
        discoverSourceRoots();
    }
    
    /**
     * Discover source roots (src/main/java, src/test/java, etc.)
     */
    private void discoverSourceRoots() {
        Path mainJava = repositoryRoot.resolve("src/main/java");
        Path testJava = repositoryRoot.resolve("src/test/java");
        
        if (Files.exists(mainJava)) {
            sourceRoots.add(mainJava.toString());
        }
        if (Files.exists(testJava)) {
            sourceRoots.add(testJava.toString());
        }
    }
    
    /**
     * Scan and return all Java source files.
     */
    public List<Path> scanSourceFiles() throws IOException {
        List<Path> sourceFiles = new ArrayList<>();
        
        for (String sourceRoot : sourceRoots) {
            Path rootPath = Paths.get(sourceRoot);
            try (Stream<Path> paths = Files.walk(rootPath)) {
                paths.filter(Files::isRegularFile)
                     .filter(p -> p.toString().endsWith(".java"))
                     .forEach(sourceFiles::add);
            }
        }
        
        return sourceFiles;
    }
    
    public Path getRepositoryRoot() {
        return repositoryRoot;
    }
    
    public List<String> getSourceRoots() {
        return new ArrayList<>(sourceRoots);
    }
}

