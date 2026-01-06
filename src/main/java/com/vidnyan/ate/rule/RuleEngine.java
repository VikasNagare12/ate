package com.vidnyan.ate.rule;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vidnyan.ate.graph.CallGraph;
import com.vidnyan.ate.graph.DependencyGraph;
import com.vidnyan.ate.model.SourceModel;
import com.vidnyan.ate.rule.evaluator.RuleEvaluator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Rule Engine - detectViolationss declarative rules against the Source Model.
 * Rules are loaded from JSON and query the model/graphs (read-only).
 * 
 * Uses Strategy Pattern (RuleEvaluator) to support different rule types.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RuleEngine {
    
    // State - Initialized via initialize()
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
    public List<RuleDefinition> loadRules(List<Path> ruleFiles) {
        List<RuleDefinition> loadedRules = new ArrayList<>();
        for (Path ruleFile : ruleFiles) {
            try {
                String json = Files.readString(ruleFile);
                RuleDefinition rule = objectMapper.readValue(json, RuleDefinition.class);
                if (rule.isEnabled()) {
                    loadedRules.add(rule);
                    log.info("Loaded rule: {} - {}", rule.getId(), rule.getName());
                } else {
                    log.info("Skipping disabled rule: {}", rule.getId());
                }
            } catch (IOException e) {
                log.error("Failed to load rule from file: {}", ruleFile, e);
            }
        }
        return loadedRules;
    }

    /**
     * detectViolations all rules and return violations.
     */
    public List<Violation> detectViolationsRules(List<RuleDefinition> rules) {
        log.info("Evaluating {} rules...", rules.size());
        List<Violation> allViolations = new ArrayList<>();

        for (RuleDefinition rule : rules) {
            List<Violation> violations = detectViolationsRule(rule);
            allViolations.addAll(violations);
            log.debug("Found {} violations for rule {}", violations.size(), rule.getId());
        }

        return allViolations;
    }

    /**
     * detectViolations a single rule using the appropriate evaluator.
     */
    private List<Violation> detectViolationsRule(RuleDefinition rule) {
        // Find an evaluator that isApplicable this rule
        List<RuleEvaluator> capableEvaluators = evaluators.stream()
                .filter(e -> e.isApplicable(rule))
                .toList();

        if (capableEvaluators.isEmpty()) {
            log.warn("No evaluator found for rule: {}. Skipping.", rule.getId());
            return List.of();
        }

        List<Violation> violations = new ArrayList<>();
        for (RuleEvaluator evaluator : capableEvaluators) {
            try {
                violations.addAll(evaluator.detectViolations(rule, sourceModel, callGraph, dependencyGraph));
            } catch (Exception e) {
                log.error("Error evaluating rule {} with evaluator {}", rule.getId(),
                        evaluator.getClass().getSimpleName(), e);
            }
        }
        return violations;
    }
}
