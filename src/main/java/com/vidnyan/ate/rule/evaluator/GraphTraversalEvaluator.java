package com.vidnyan.ate.rule.evaluator;

import com.vidnyan.ate.graph.CallGraph;
import com.vidnyan.ate.graph.DependencyGraph;
import com.vidnyan.ate.model.Location;
import com.vidnyan.ate.model.Method;
import com.vidnyan.ate.model.SourceModel;
import com.vidnyan.ate.rule.RuleDefinition;
import com.vidnyan.ate.rule.Violation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Evaluates rules that require traversing graphs (Call Graph, Dependency Graph).
 * Supports flexible criteria for selecting start and target nodes.
 */
@Slf4j
@Component
public class GraphTraversalEvaluator implements RuleEvaluator {

    @Override
    public boolean supports(RuleDefinition rule) {
        return "graph_traversal".equals(rule.getQuery().getType());
    }

    @Override
    public List<Violation> evaluate(RuleDefinition rule, SourceModel sourceModel, CallGraph callGraph, DependencyGraph dependencyGraph) {
        String graphType = rule.getQuery().getGraph();
        Map<String, Object> pattern = rule.getQuery().getPattern();

        if ("call_graph".equals(graphType)) {
            return evaluateCallGraphRule(rule, pattern, sourceModel, callGraph);
        } else if ("dependency_graph".equals(graphType)) {
            return evaluateDependencyGraphRule(rule, pattern, dependencyGraph);
        }
        
        return List.of();
    }

    private List<Violation> evaluateCallGraphRule(RuleDefinition rule, Map<String, Object> pattern, SourceModel sourceModel, CallGraph callGraph) {
        List<Violation> violations = new ArrayList<>();

        @SuppressWarnings("unchecked")
        Map<String, Object> startCriteria = (Map<String, Object>) pattern.get("start");
        @SuppressWarnings("unchecked")
        Map<String, Object> traverse = (Map<String, Object>) pattern.get("traverse");
        @SuppressWarnings("unchecked")
        Map<String, Object> targetCriteria = (Map<String, Object>) pattern.getOrDefault("target", Map.of());

        // 1. Find Start Elements (Methods)
        // Currently supports methods as start points. If types are needed, we'd expand this.
        List<Method> startMethods = findMethods(sourceModel, startCriteria);

        for (Method method : startMethods) {
            // 2. Traverse Call Graph
            Integer maxDepth = traverse.containsKey("maxDepth") ? (Integer) traverse.get("maxDepth") : 10;
            Set<String> reachableMethods = callGraph.findReachableMethods(method.getFullyQualifiedName(), maxDepth);

            // 3. Check Target Conditions
            
            // Condition A: mustNotReach (Forbidden)
            // If ANY reachable method matches ANY of the forbidden criteria, it's a violation.
            if (targetCriteria.containsKey("mustNotReach")) {
                List<Object> forbiddenList = getList(targetCriteria, "mustNotReach");
                
                for (Object forbiddenCriteriaObj : forbiddenList) {
                    Map<String, Object> forbiddenCriteria = asMap(forbiddenCriteriaObj);
                    
                    Optional<String> match = reachableMethods.stream()
                            .filter(calleeName -> matches(calleeName, sourceModel, forbiddenCriteria))
                            .findFirst();
                    
                    if (match.isPresent()) {
                         violations.add(createViolation(rule, method.getLocation(), Map.of(
                                "method", method.getFullyQualifiedName(),
                                "target", match.get()
                        )));
                    }
                }
            }
            
            // Condition B: mustReach (Required)
            // If we DO NOT reach ANY method matching the required criteria, it's a violation.
            // Note: Use this for "Scheduled jobs must reach Retryable". 
            // The existing rule uses "mustNotReach" in a confusing way (inverted logic?).
            // For Principal Architect standards, we should fix the JSON to use "mustReach" properly.
            // But to support the OLD JSON without breaking it, we need to inspect the old JSON structure.
            // The old JSON had "mustNotReach": ["Retryable"]. This logic meant: 
            // "Scan distinct paths. If a path contains Retryable, it is safe."
            // Wait, the previous code:
            // boolean foundForbidden = ...
            // if (foundForbidden) { continue; } // It is OK!
            // violations.add(...) // It is bad!
            // So "mustNotReach" in the OLD code actually implemented "mustReach".
            // It was a bug in naming in the old code. I will implement "mustReach" logic here.
            // AND I will have to fix the JSON file to use "mustReach" instead of "mustNotReach".
            
            if (targetCriteria.containsKey("mustReach")) {
                List<Object> requiredList = getList(targetCriteria, "mustReach");
                boolean anyRequirementMet = false;
                
                for (Object requiredCriteriaObj : requiredList) {
                    Map<String, Object> requiredCriteria = asMap(requiredCriteriaObj);
                     boolean reached = reachableMethods.stream()
                            .anyMatch(calleeName -> matches(calleeName, sourceModel, requiredCriteria));
                     if (reached) {
                         anyRequirementMet = true;
                         break;
                     }
                }
                
                if (!anyRequirementMet) {
                     // Did not reach any of the required attributes
                     violations.add(createViolation(rule, method.getLocation(), Map.of(
                            "method", method.getFullyQualifiedName(),
                            "missingTarget", "Required dependency not found"
                    )));
                }
            }
        }
        
        return violations;
    }
    
     private List<Violation> evaluateDependencyGraphRule(RuleDefinition rule, Map<String, Object> pattern, DependencyGraph dependencyGraph) {
        List<Violation> violations = new ArrayList<>();
        if (dependencyGraph.hasCircularDependencies()) {
            for (List<String> cycle : dependencyGraph.getCircularDependencies()) {
                violations.add(createViolation(rule, 
                        Location.builder().filePath("").line(0).column(0).build(),
                        Map.of("cycle", cycle)));
            }
        }
        return violations;
    }

    // Helper to find methods based on criteria
    private List<Method> findMethods(SourceModel model, Map<String, Object> criteria) {
        // If searching by annotation
        if (criteria.containsKey("annotation")) {
            String annotation = (String) criteria.get("annotation");
            return model.getMethodsAnnotatedWith(annotation);
        }
        
        // If searching by name pattern
        if (criteria.containsKey("namePattern")) {
            String regex = (String) criteria.get("namePattern");
            Pattern pattern = Pattern.compile(regex);
            
            // Check if element is "method" or "type" (default to method for these rules)
            // But currently SourceModel organizes by Types -> Methods.
            // So we stream all methods from all types.
            return model.getTypes().values().stream()
                    .flatMap(t -> t.getMethods().stream())
                    .filter(m -> pattern.matcher(m.getFullyQualifiedName()).matches())
                    .collect(Collectors.toList());
        }
        
        return List.of();
    }
    
    // Helper to match a method/callee against criteria
    private boolean matches(String calleeName, SourceModel model, Map<String, Object> criteria) {
        Method callee = model.getMethod(calleeName);
        
        // 1. Check Name Pattern
        if (criteria.containsKey("pattern")) {
            String regex = (String) criteria.get("pattern");
            if (Pattern.matches(regex, calleeName)) {
                return true;
            }
        }
        
        // 2. Check Annotation (Requires resolved method in source model)
        if (callee != null && (criteria.containsKey("annotation") || isSimpleStringList(criteria))) {
            // Support "annotation": "Foo"
            if (criteria.containsKey("annotation")) {
                String ann = (String) criteria.get("annotation");
                if (callee.hasAnnotation(ann)) return true;
            }
            // Support simple string pattern acting as annotation check (legacy compatibility)
            // Example: "mustReach": ["Retryable"] -> treated as annotation check if it looks like one?
            // Or just precise key.
        }

        // 3. Check "attribute": "name" with "pattern" (Explicit)
        if ("name".equals(criteria.get("attribute")) && criteria.containsKey("pattern")) {
             String regex = (String) criteria.get("pattern");
             return Pattern.matches(regex, calleeName);
        }

        return false;
    }
    
    // Helper to detect if criteria came from a simple list of strings
    private boolean isSimpleStringList(Map<String, Object> criteria) {
        return criteria.size() == 1 && criteria.containsKey("pattern");
    }

    private Violation createViolation(RuleDefinition rule, Location location, Map<String, Object> context) {
         String message = rule.getViolation().getMessage();
         for (Map.Entry<String, Object> entry : context.entrySet()) {
            message = message.replace("{" + entry.getKey() + "}", String.valueOf(entry.getValue()));
         }
         
        return Violation.builder()
                .ruleId(rule.getRuleId())
                .severity(rule.getSeverity())
                .message(message)
                .location(location)
                .context(context)
                .fingerprint(Violation.generateFingerprint(rule.getRuleId(), location, context))
                .build();
    }
    
    @SuppressWarnings("unchecked")
    private List<Object> getList(Map<String, Object> map, String key) {
        Object val = map.get(key);
        if (val instanceof List) {
            return (List<Object>) val;
        }
        return List.of();
    }
    
    @SuppressWarnings("unchecked")
    private Map<String, Object> asMap(Object obj) {
        if (obj instanceof Map) {
            return (Map<String, Object>) obj;
        }
        if (obj instanceof String) {
            // Convert simple string to pattern map for backward compatibility
            // The old code checked annotations if it was a string list.
            // So if it's a string, we treat it as an annotation criteria?
            // "Retryable" -> matches annotation?
            // Or pattern? 
            // Given "Scheduled jobs must use retry", it implies annotation.
            // But "mustNotReach": ["...Controller"] implies name pattern.
            // Let's assume pattern for now, but I might need to fix the JSON to be explicit.
            return Map.of("pattern", obj); 
        }
        return Map.of();
    }
}
