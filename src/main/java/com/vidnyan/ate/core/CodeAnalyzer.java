package com.vidnyan.ate.core;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Code Analyzer using ArchUnit for parsing.
 */
@Component
public class CodeAnalyzer {

    private static final Logger log = LoggerFactory.getLogger(CodeAnalyzer.class);

    /**
     * Analyze compiled classes from a path.
     */
    public AnalysisContext analyze(Path classesPath) {
        log.info("Analyzing classes from: {}", classesPath);

        JavaClasses classes = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPath(classesPath);

        log.info("Imported {} classes", classes.size());

        return new AnalysisContext(classes);
    }

    /**
     * Analyze classes from a package.
     */
    public AnalysisContext analyzePackages(String... packages) {
        log.info("Analyzing packages: {}", String.join(", ", packages));

        JavaClasses classes = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages(packages);

        log.info("Imported {} classes", classes.size());

        return new AnalysisContext(classes);
    }

    /**
     * Context holding the analyzed code.
     */
    public record AnalysisContext(JavaClasses classes) {

        /**
         * Get all methods with a specific annotation by name.
         */
        public Set<JavaMethod> getMethodsWithAnnotation(String annotationSimpleName) {
            return classes.stream()
                    .flatMap(c -> c.getMethods().stream())
                    .filter(m -> m.getAnnotations().stream()
                            .anyMatch(a -> a.getRawType().getSimpleName().equals(annotationSimpleName)))
                    .collect(Collectors.toSet());
        }

        /**
         * Get all methods that call a specific method.
         */
        public Set<JavaMethod> getMethodsCalling(String targetClassName, String targetMethodName) {
            return classes.stream()
                    .flatMap(c -> c.getMethods().stream())
                    .filter(m -> m.getMethodCallsFromSelf().stream()
                            .anyMatch(call -> call.getTargetOwner().getName().contains(targetClassName) &&
                                    call.getName().equals(targetMethodName)))
                    .collect(Collectors.toSet());
        }

        /**
         * Get outgoing calls from a method.
         */
        public List<MethodCall> getOutgoingCalls(JavaMethod method) {
            return method.getMethodCallsFromSelf().stream()
                    .map(call -> new MethodCall(
                            method.getFullName(),
                            call.getTargetOwner().getName() + "." + call.getName(),
                            call.getLineNumber()))
                    .toList();
        }

        /**
         * Get all annotations on a method as strings.
         */
        public List<String> getAnnotations(JavaMethod method) {
            return method.getAnnotations().stream()
                    .map(a -> "@" + a.getRawType().getSimpleName())
                    .toList();
        }
    }

    /**
     * Represents a method call.
     */
    public record MethodCall(
            String caller,
            String callee,
            int lineNumber) {
    }
}
