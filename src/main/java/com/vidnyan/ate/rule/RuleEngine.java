package com.vidnyan.ate.rule;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vidnyan.ate.graph.CallGraph;
import com.vidnyan.ate.graph.DependencyGraph;
import com.vidnyan.ate.model.*;
import com.vidnyan.ate.rule.evaluator.RuleEvaluator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Rule Engine - evaluates declarative rules against the Source Model.
 * Rules are loaded from JSON and query the model/graphs (read-only).
 * 
 * Uses Strategy Pattern (RuleEvaluator) to support different rule types.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RuleEngine {
    
    private SourceModel sourceModel;
    private CallGraph callGraph;
    private DependencyGraph dependencyGraph;
    
    private final ObjectMapper objectMapper = new ObjectMapper(); // Or inject if configured as bean
    private final List<RuleEvaluator> evaluators;
    
    /**
     * Initialize rule engine with model and graphs.
     */
    public void initialize(SourceModel sourceModel, CallGraph callGraph, DependencyGraph dependencyGraph) {
        this.sourceModel = sourceModel;
        this.callGraph = callGraph;
        this.dependencyGraph = dependencyGraph;
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
     * Evaluate a single rule using the appropriate evaluator.
     */
    private List<Violation> evaluateRule(RuleDefinition rule) {
        return evaluators.stream()
                .filter(evaluator -> evaluator.supports(rule))
                .findFirst()
                .map(evaluator -> evaluator.evaluate(rule, sourceModel, callGraph, dependencyGraph))
                .orElseGet(() -> {
                    log.warn("No evaluator found for rule type: {}", rule.getQuery().getType());
                    return List.of();
                });
    }
}

