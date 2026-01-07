package com.vidnyan.ate.adapter.out.evaluator;

import com.vidnyan.ate.domain.graph.CallGraph;
import com.vidnyan.ate.domain.model.Location;
import com.vidnyan.ate.domain.model.MethodEntity;
import com.vidnyan.ate.domain.model.SourceModel;
import com.vidnyan.ate.domain.rule.*;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Generic path reachability evaluator.
 * Detects if any sink is reachable from an entry point.
 * 
 * This is a powerful generic evaluator that handles many rules:
 * - Transaction boundary violations (entry: @Transactional, sink: RestTemplate)
 * - Async safety issues (entry: @Async, sink: @Transactional)
 * - And more...
 */
@Slf4j
@Component

public class PathReachabilityEvaluator implements RuleEvaluator {
    
    private static final int MAX_DEPTH = 50;
    
    @Override
    public boolean supports(RuleDefinition rule) {
        // Supports rules that have both entry points and sinks defined
        return rule.detection() != null 
                && rule.detection().entryPoints() != null
                && rule.detection().sinks() != null
                && (!rule.detection().entryPoints().annotations().isEmpty()
                    || !rule.detection().entryPoints().types().isEmpty())
                && (!rule.detection().sinks().types().isEmpty()
                    || !rule.detection().sinks().annotations().isEmpty());
    }
    
    @Override
    public EvaluationResult evaluate(EvaluationContext context) {
        Instant start = Instant.now();
        RuleDefinition rule = context.rule();
        SourceModel model = context.sourceModel();
        CallGraph callGraph = context.callGraph();
        
        log.debug("Evaluating rule {} with PathReachabilityEvaluator", rule.id());
        
        List<Violation> violations = new ArrayList<>();
        int nodesAnalyzed = 0;
        
        // Find all entry point methods
        List<MethodEntity> entryMethods = findEntryMethods(model, rule.detection().entryPoints());
        log.debug("Found {} entry point methods", entryMethods.size());
        
        // For each entry method, check if any sink is reachable
        for (MethodEntity entryMethod : entryMethods) {
            nodesAnalyzed++;
            
            // Find call chains to sinks
            List<List<String>> chains = findChainsToSinks(
                    entryMethod.fullyQualifiedName(),
                    rule.detection().sinks(),
                    callGraph,
                    rule.detection().pathConstraints()
            );
            
            // Create violation for each chain found
            for (List<String> chain : chains) {
                String sinkMethod = chain.get(chain.size() - 1);
                
                violations.add(Violation.builder()
                        .ruleId(rule.id())
                        .ruleName(rule.name())
                        .severity(rule.severity())
                        .message(buildViolationMessage(rule, entryMethod, sinkMethod))
                        .location(entryMethod.location())
                        .callChain(chain)
                        .build());
            }
        }
        
        Duration duration = Duration.between(start, Instant.now());
        log.debug("Rule {} found {} violations in {}ms", 
                rule.id(), violations.size(), duration.toMillis());
        
        return EvaluationResult.success(rule.id(), violations, duration, nodesAnalyzed);
    }
    
    private List<MethodEntity> findEntryMethods(
            SourceModel model, 
            RuleDefinition.Detection.EntryPoints entryPoints
    ) {
        List<MethodEntity> entries = new ArrayList<>();
        
        // Find methods with entry point annotations
        for (String annotation : entryPoints.annotations()) {
            String simpleName = annotation.contains(".") 
                    ? annotation.substring(annotation.lastIndexOf('.') + 1) 
                    : annotation;
            
            entries.addAll(model.findMethodsWithAnnotation(simpleName));
        }
        
        // Find methods in entry point types
        for (String typeFqn : entryPoints.types()) {
            entries.addAll(model.getMethodsInType(typeFqn));
        }
        
        return entries;
    }
    
    private List<List<String>> findChainsToSinks(
            String startMethod,
            RuleDefinition.Detection.Sinks sinks,
            CallGraph callGraph,
            RuleDefinition.Detection.PathConstraints constraints
    ) {
        List<List<String>> allChains = new ArrayList<>();
        
        // Build sink patterns from types
        List<String> sinkPatterns = new ArrayList<>();
        for (String sinkType : sinks.types()) {
            sinkPatterns.add(sinkType + "#");
        }
        
        // Find chains to each sink pattern
        for (String pattern : sinkPatterns) {
            List<List<String>> chains = callGraph.findChainsToSink(startMethod, pattern);
            
            // Filter by path constraints
            for (List<String> chain : chains) {
                if (satisfiesConstraints(chain, constraints)) {
                    allChains.add(chain);
                }
            }
        }
        
        return allChains;
    }
    
    private boolean satisfiesConstraints(
            List<String> chain, 
            RuleDefinition.Detection.PathConstraints constraints
    ) {
        if (constraints == null) return true;
        
        // Check max depth
        if (chain.size() > constraints.maxDepth()) {
            return false;
        }
        
        // Check mustContain
        for (String required : constraints.mustContain()) {
            boolean found = chain.stream().anyMatch(m -> m.contains(required));
            if (!found) return false;
        }
        
        // Check mustNotContain
        for (String forbidden : constraints.mustNotContain()) {
            boolean found = chain.stream().anyMatch(m -> m.contains(forbidden));
            if (found) return false;
        }
        
        return true;
    }
    
    private String buildViolationMessage(
            RuleDefinition rule, 
            MethodEntity entryMethod, 
            String sinkMethod
    ) {
        String entryAnnotation = rule.detection().entryPoints().annotations().isEmpty() 
                ? "" : "@" + getSimpleName(rule.detection().entryPoints().annotations().get(0));
        String sinkType = rule.detection().sinks().types().isEmpty()
                ? "sink" : getSimpleName(rule.detection().sinks().types().get(0));
        
        return String.format(
                "%s method '%s' calls %s which may cause %s",
                entryAnnotation,
                entryMethod.simpleName(),
                sinkType,
                rule.name().toLowerCase()
        );
    }
    
    private String getSimpleName(String fqn) {
        return fqn.contains(".") ? fqn.substring(fqn.lastIndexOf('.') + 1) : fqn;
    }
}
