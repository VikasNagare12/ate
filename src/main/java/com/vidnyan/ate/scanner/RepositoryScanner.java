package com.vidnyan.ate.scanner;


import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * Scans repository for Java source files.
 * Discovers all .java files in the source roots.
 */
@Component
public class RepositoryScanner {
    
    /**
     * Scan and return all Java source files in the given repository root.
     * 
     * @param repositoryRoot The root directory of the repository to scan
     * @return List of paths to Java source files
     * @throws IOException If an I/O error occurs
     */
    public List<Path> scanSourceFiles(Path repositoryRoot) throws IOException {
        List<Path> sourceFiles = new ArrayList<>();
        List<Path> sourceRoots = discoverSourceRoots(repositoryRoot);
        
        for (Path sourceRoot : sourceRoots) {
            try (Stream<Path> paths = Files.walk(sourceRoot)) {
                paths.filter(Files::isRegularFile)
                     .filter(p -> p.toString().endsWith(".java"))
                     .forEach(sourceFiles::add);
            }
        }
        
        return sourceFiles;
    }

    /**
     * Discover source roots (src/main/java, src/test/java, etc.)
     */
    private List<Path> discoverSourceRoots(Path repositoryRoot) {
        List<Path> roots = new ArrayList<>();
        Path mainJava = repositoryRoot.resolve("src/main/java");
        
        if (Files.exists(mainJava)) {
            roots.add(mainJava);
        }
        // Add other conventions here if needed
        
        return roots;
    }
}

