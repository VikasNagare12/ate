package com.vidnyan.ate.rule;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vidnyan.ate.graph.CallGraph;
import com.vidnyan.ate.graph.DependencyGraph;
import com.vidnyan.ate.model.*;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Rule Engine - evaluates declarative rules against the Source Model.
 * Rules are loaded from JSON and query the model/graphs (read-only).
 */
@Slf4j
public class RuleEngine {
    
    private final SourceModel sourceModel;
    private final CallGraph callGraph;
    private final DependencyGraph dependencyGraph;
    private final ObjectMapper objectMapper;
    
    public RuleEngine(SourceModel sourceModel, CallGraph callGraph, DependencyGraph dependencyGraph) {
        this.sourceModel = sourceModel;
        this.callGraph = callGraph;
        this.dependencyGraph = dependencyGraph;
        this.objectMapper = new ObjectMapper();
    }
    
    /**
     * Load rules from JSON files.
     */
    public List<RuleDefinition> loadRules(List<Path> ruleFiles) throws IOException {
        List<RuleDefinition> rules = new ArrayList<>();
        for (Path ruleFile : ruleFiles) {
            String json = Files.readString(ruleFile);
            RuleDefinition rule = objectMapper.readValue(json, RuleDefinition.class);
            rules.add(rule);
            log.info("Loaded rule: {}", rule.getRuleId());
        }
        return rules;
    }
    
    /**
     * Evaluate all rules and return violations.
     */
    public List<Violation> evaluateRules(List<RuleDefinition> rules) {
        List<Violation> allViolations = new ArrayList<>();
        
        for (RuleDefinition rule : rules) {
            log.info("Evaluating rule: {}", rule.getRuleId());
            List<Violation> violations = evaluateRule(rule);
            allViolations.addAll(violations);
            log.info("Found {} violations for rule {}", violations.size(), rule.getRuleId());
        }
        
        return allViolations;
    }
    
    /**
     * Evaluate a single rule.
     */
    private List<Violation> evaluateRule(RuleDefinition rule) {
        String queryType = rule.getQuery().getType();
        
        return switch (queryType) {
            case "graph_traversal" -> evaluateGraphTraversalRule(rule);
            case "model_query" -> evaluateModelQueryRule(rule);
            case "pattern_match" -> evaluatePatternMatchRule(rule);
            default -> {
                log.warn("Unknown query type: {}", queryType);
                yield List.of();
            }
        };
    }
    
    /**
     * Evaluate a graph traversal rule.
     */
    private List<Violation> evaluateGraphTraversalRule(RuleDefinition rule) {
        List<Violation> violations = new ArrayList<>();
        
        String graphType = rule.getQuery().getGraph();
        Map<String, Object> pattern = rule.getQuery().getPattern();
        
        if ("call_graph".equals(graphType)) {
            // Example: Find @Scheduled methods that don't call retry logic
            @SuppressWarnings("unchecked")
            Map<String, Object> start = (Map<String, Object>) pattern.get("start");
            @SuppressWarnings("unchecked")
            Map<String, Object> traverse = (Map<String, Object>) pattern.get("traverse");
            @SuppressWarnings("unchecked")
            Map<String, Object> target = (Map<String, Object>) pattern.getOrDefault("target", Map.of());
            
            String annotation = (String) start.get("annotation");
            String element = (String) start.get("element");
            
            if ("method".equals(element)) {
                // Find methods with annotation
                List<Method> startMethods = sourceModel.getMethodsAnnotatedWith(annotation);
                
                for (Method method : startMethods) {
                    // Traverse call graph
                    Integer maxDepth = traverse.containsKey("maxDepth") ? 
                            (Integer) traverse.get("maxDepth") : 10;
                    Set<String> reachable = callGraph.findReachableMethods(
                            method.getFullyQualifiedName(), maxDepth);
                    
                    // Check target criteria
                    if (target.containsKey("mustNotReach")) {
                        @SuppressWarnings("unchecked")
                        List<String> forbiddenAnnotations = (List<String>) target.get("mustNotReach");
                        
                        boolean foundForbidden = reachable.stream()
                                .anyMatch(callee -> {
                                    Method calleeMethod = sourceModel.getMethod(callee);
                                    if (calleeMethod != null) {
                                        return forbiddenAnnotations.stream()
                                                .anyMatch(ann -> calleeMethod.hasAnnotation(ann));
                                    }
                                    return false;
                                });
                        
                        if (foundForbidden) {
                            // This is actually OK - it reaches the required annotation
                            continue;
                        }
                        
                        // Violation: doesn't reach required annotation
                        violations.add(createViolation(rule, method.getLocation(), Map.of(
                                "method", method.getFullyQualifiedName(),
                                "annotation", annotation
                        )));
                    }
                }
            }
        } else if ("dependency_graph".equals(graphType)) {
            // Example: Detect circular dependencies
            if (dependencyGraph.hasCircularDependencies()) {
                for (List<String> cycle : dependencyGraph.getCircularDependencies()) {
                    violations.add(createViolation(rule, 
                            Location.builder()
                                    .filePath("")
                                    .line(0)
                                    .column(0)
                                    .build(),
                            Map.of("cycle", cycle)));
                }
            }
        }
        
        return violations;
    }
    
    /**
     * Evaluate a model query rule.
     */
    private List<Violation> evaluateModelQueryRule(RuleDefinition rule) {
        // TODO: Implement model query evaluation
        return List.of();
    }
    
    /**
     * Evaluate a pattern match rule.
     */
    private List<Violation> evaluatePatternMatchRule(RuleDefinition rule) {
        // TODO: Implement pattern matching
        return List.of();
    }
    
    /**
     * Create a violation from rule definition and context.
     */
    private Violation createViolation(RuleDefinition rule, Location location, 
                                     Map<String, Object> context) {
        String message = formatMessage(rule.getViolation().getMessage(), context);
        String fingerprint = Violation.generateFingerprint(rule.getRuleId(), location, context);
        
        return Violation.builder()
                .ruleId(rule.getRuleId())
                .severity(rule.getSeverity())
                .message(message)
                .location(location)
                .context(context)
                .fingerprint(fingerprint)
                .build();
    }
    
    /**
     * Format violation message with context placeholders.
     */
    private String formatMessage(String template, Map<String, Object> context) {
        String result = template;
        for (Map.Entry<String, Object> entry : context.entrySet()) {
            result = result.replace("{" + entry.getKey() + "}", 
                    String.valueOf(entry.getValue()));
        }
        return result;
    }
}

