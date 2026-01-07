package com.vidnyan.ate.adapter.out.evaluator;

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
 * Detects @Async methods that also have @Transactional.
 * The transaction context does NOT propagate to async methods.
 */
@Slf4j
@Component

public class AsyncTransactionEvaluatorV2 implements RuleEvaluator {
    
    private static final String ASYNC = "Async";
    private static final String TRANSACTIONAL = "Transactional";
    
    @Override
    public boolean supports(RuleDefinition rule) {
        // Only match the specific async-transaction rule
        return "ASYNC-TX-001".equals(rule.id());
    }
    
    @Override
    public EvaluationResult evaluate(EvaluationContext context) {
        Instant start = Instant.now();
        SourceModel model = context.sourceModel();
        RuleDefinition rule = context.rule();
        
        log.debug("Evaluating @Async + @Transactional conflicts");
        
        List<Violation> violations = new ArrayList<>();
        int nodesAnalyzed = 0;
        
        // Find all @Async methods
        List<MethodEntity> asyncMethods = model.findMethodsWithAnnotation(ASYNC);
        log.debug("Found {} @Async methods", asyncMethods.size());
        
        for (MethodEntity method : asyncMethods) {
            nodesAnalyzed++;
            
            // Check if also has @Transactional
            if (method.hasAnnotation(TRANSACTIONAL)) {
                violations.add(Violation.builder()
                        .ruleId(rule.id())
                        .ruleName(rule.name())
                        .severity(rule.severity())
                        .message(String.format(
                                "Method '%s' has both @Async and @Transactional. " +
                                "Transaction context does NOT propagate to async threads. " +
                                "The @Transactional on the async method starts a NEW transaction.",
                                method.simpleName()
                        ))
                        .location(method.location())
                        .callChain(List.of(method.fullyQualifiedName()))
                        .build());
            }
        }
        
        Duration duration = Duration.between(start, Instant.now());
        return EvaluationResult.success(rule.id(), violations, duration, nodesAnalyzed);
    }
}
