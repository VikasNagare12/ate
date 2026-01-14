package com.vidnyan.ate.analyzer;

import sootup.callgraph.CallGraph;
import sootup.core.inputlocation.AnalysisInputLocation;
import sootup.core.model.SootClass;
import sootup.core.views.View;
import sootup.java.bytecode.inputlocation.JavaClassPathAnalysisInputLocation;
import sootup.java.core.views.JavaView;

import java.nio.file.Path;
import java.util.Collections;
import java.util.List;

public class SootUpAnalyzer {

    private final Path classesPath;
    private final Path sourcePath;

    public SootUpAnalyzer(Path classesPath, Path sourcePath) {
        this.classesPath = classesPath;
        this.sourcePath = sourcePath;
    }

    public View analyze() {
        Path inputPath = classesPath;

        // Support JAR analysis by extracting to temp dir
        // We handle any regular file as a potential JAR/archive to be extracted
        if (java.nio.file.Files.isRegularFile(classesPath)) {
            try {
                Path tempDir = java.nio.file.Files.createTempDirectory("ate-analysis-");
                tempDir.toFile().deleteOnExit();
                extractJar(classesPath, tempDir);
                inputPath = tempDir;
                // Note: In production we should clean up, but deleteOnExit helps.
            } catch (Exception e) {
                throw new RuntimeException("Failed to extract JAR for analysis", e);
            }
        }

        // 1. Setup Input Location
        AnalysisInputLocation inputLocation = new JavaClassPathAnalysisInputLocation(
                inputPath.toAbsolutePath().toString());

        // 2. Create View
        View view = new JavaView(inputLocation);

        return view;
    }

    private void extractJar(Path jarPath, Path metaInfDir) throws java.io.IOException {
        try (java.util.jar.JarFile jar = new java.util.jar.JarFile(jarPath.toFile())) {
            java.util.Enumeration<java.util.jar.JarEntry> entries = jar.entries();
            while (entries.hasMoreElements()) {
                java.util.jar.JarEntry entry = entries.nextElement();
                java.io.File dest = new java.io.File(metaInfDir.toFile(), entry.getName());
                if (entry.isDirectory()) {
                    dest.mkdirs();
                } else {
                    dest.getParentFile().mkdirs();
                    try (java.io.InputStream is = jar.getInputStream(entry);
                            java.io.FileOutputStream fos = new java.io.FileOutputStream(dest)) {
                        is.transferTo(fos);
                    }
                }
            }
        }
    }
}
