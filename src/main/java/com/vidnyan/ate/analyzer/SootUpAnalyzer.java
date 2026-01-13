package com.vidnyan.ate.analyzer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import sootup.core.model.SootMethod;
import sootup.core.signatures.MethodSignature;
import sootup.java.bytecode.inputlocation.JavaClassPathAnalysisInputLocation;
import sootup.java.core.JavaSootClass;
import sootup.java.core.views.JavaView;
import sootup.callgraph.CallGraph;
import sootup.callgraph.ClassHierarchyAnalysisAlgorithm;

import java.nio.file.Path;
import java.util.*;

/**
 * SootUp-based analyzer for deep call graph analysis.
 * Focuses ONLY on call graph - annotations come from JavaParser.
 */
@Component
public class SootUpAnalyzer {

    private static final Logger log = LoggerFactory.getLogger(SootUpAnalyzer.class);

    /**
     * Analyze compiled classes and build call graph.
     */
    public CallGraphResult analyze(Path classesPath) {
        log.info("SootUp: Analyzing bytecode from: {}", classesPath);

        Map<String, MethodCallChain> methodChains = new HashMap<>();
        CallGraph callGraph = null;

        try {
            // Create input location
            JavaClassPathAnalysisInputLocation inputLocation = new JavaClassPathAnalysisInputLocation(
                    classesPath.toString());

            // Create view
            JavaView view = new JavaView(inputLocation);

            // Get all classes as a list
            List<JavaSootClass> classes = new ArrayList<>();
            view.getClasses().forEach(classes::add);
            log.info("SootUp: Found {} classes", classes.size());

            // Collect method signatures for entry points
            List<MethodSignature> entryPoints = new ArrayList<>();
            for (JavaSootClass clazz : classes) {
                for (SootMethod m : clazz.getMethods()) {
                    entryPoints.add(m.getSignature());
                }
            }

            // Build call graph using Class Hierarchy Analysis
            if (!entryPoints.isEmpty()) {
                try {
                    ClassHierarchyAnalysisAlgorithm cha = new ClassHierarchyAnalysisAlgorithm(view);
                    callGraph = cha.initialize(entryPoints);
                    log.info("SootUp: Built call graph with {} call edges", callGraph.callCount());
                } catch (Exception e) {
                    log.warn("SootUp: Could not build call graph: {}", e.getMessage());
                }
            }

            // Extract method call chains from call graph
            final CallGraph finalCallGraph = callGraph;
            for (JavaSootClass clazz : classes) {
                for (SootMethod sootMethod : clazz.getMethods()) {
                    String fqn = sootMethod.getSignature().toString();

                    // Get call chain from call graph (annotations come from JavaParser)
                    List<String> callChain = new ArrayList<>();
                    if (finalCallGraph != null) {
                        collectCallChain(finalCallGraph, sootMethod.getSignature(), callChain, 5, new HashSet<>());
                    }

                    methodChains.put(fqn, new MethodCallChain(
                            fqn,
                            clazz.getName(),
                            sootMethod.getName(),
                            callChain));
                }
            }

            log.info("SootUp: Analyzed {} methods with call chains", methodChains.size());
            return new CallGraphResult(methodChains, callGraph);

        } catch (Exception e) {
            log.error("SootUp: Failed to analyze", e);
            return new CallGraphResult(Map.of(), null);
        }
    }

    /**
     * Recursively collect call chain up to a depth.
     */
    private void collectCallChain(CallGraph cg, MethodSignature method, List<String> chain,
            int depth, Set<String> visited) {
        if (depth <= 0 || visited.contains(method.toString())) {
            return;
        }
        visited.add(method.toString());

        try {
            cg.callsFrom(method).forEach(callee -> {
                chain.add(callee.toString());
                collectCallChain(cg, callee, chain, depth - 1, visited);
            });
        } catch (Exception e) {
            // Skip if method not found in call graph
        }
    }

    /**
     * Result of call graph analysis.
     */
    public record CallGraphResult(
            Map<String, MethodCallChain> methods,
            CallGraph callGraph) {
        /**
         * Get full call chain from a method.
         */
        public List<String> getDeepCallChain(String methodFqn, int maxDepth) {
            return methods.values().stream()
                    .filter(m -> m.fqn().contains(methodFqn))
                    .findFirst()
                    .map(MethodCallChain::callChain)
                    .orElse(List.of());
        }
    }

    /**
     * Method with its call chain (annotations come from JavaParser).
     */
    public record MethodCallChain(
            String fqn,
            String className,
            String methodName,
            List<String> callChain) {
    }
}
