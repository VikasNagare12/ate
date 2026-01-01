package com.vidnyan.ate.scanner;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RepositoryScannerTest {

    @TempDir
    Path tempDir;

    @Test
    void scanSourceFiles_ShouldFindJavaFiles() throws IOException {
        // Arrange
        RepositoryScanner scanner = new RepositoryScanner();
        
        // Create valid structure
        Path mainJava = tempDir.resolve("src/main/java/com/example");
        Files.createDirectories(mainJava);
        
        Path file1 = mainJava.resolve("Test1.java");
        Path file2 = mainJava.resolve("Test2.java");
        Path txtFile = mainJava.resolve("readme.txt");
        
        Files.writeString(file1, "public class Test1 {}");
        Files.writeString(file2, "public class Test2 {}");
        Files.writeString(txtFile, "documentation");
        
        // Create invalid structure (root)
        Path rootFile = tempDir.resolve("Root.java");
        Files.writeString(rootFile, "public class Root {}");

        // Act
        List<Path> results = scanner.scanSourceFiles(tempDir);

        // Assert
        assertEquals(2, results.size());
        assertTrue(results.stream().anyMatch(p -> p.endsWith("Test1.java")));
        assertTrue(results.stream().anyMatch(p -> p.endsWith("Test2.java")));
        assertFalse(results.stream().anyMatch(p -> p.endsWith("readme.txt")));
        assertFalse(results.stream().anyMatch(p -> p.endsWith("Root.java")));
    }
}
