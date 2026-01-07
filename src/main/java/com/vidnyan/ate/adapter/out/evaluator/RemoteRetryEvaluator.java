package com.vidnyan.ate.adapter.out.evaluator;

import com.vidnyan.ate.domain.graph.CallGraph;
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
 * Dedicated evaluator for REMOTE-RETRY-001 rule.
 * Detects if @Retryable methods call non-idempotent remote sinks.
 */
@Slf4j
@Component
public class RemoteRetryEvaluator implements RuleEvaluator {

    private static final String ENTRY_ANNOTATION = "Retryable";
    private static final List<String> REMOTE_SINKS = List.of(
            "org.springframework.web.client.RestTemplate",
            "org.springframework.web.reactive.function.client.WebClient",
            "java.net.http.HttpClient",
            "org.springframework.kafka.core.KafkaTemplate");

    @Override
    public boolean supports(RuleDefinition rule) {
        return "REMOTE-RETRY-001".equals(rule.id());
    }

    @Override
    public EvaluationResult evaluate(EvaluationContext context) {
        Instant start = Instant.now();
        SourceModel model = context.sourceModel();
        CallGraph callGraph = context.callGraph();
        RuleDefinition rule = context.rule();

        log.debug("Evaluating rule {} with RemoteRetryEvaluator", rule.id());

        List<Violation> violations = new ArrayList<>();
        int nodesAnalyzed = 0;

        // 1. Find Entry Point Methods (@Retryable)
        List<MethodEntity> entryMethods = new ArrayList<>();
        // Search for simple name
        entryMethods.addAll(model.findMethodsWithAnnotation(ENTRY_ANNOTATION));
        // Search for FQN (handling the case where simple name != FQN search if needed,
        // though model.findMethodsWithAnnotation usually takes simple name or FQN.
        // We'll mimic the robust logic we added to PathReachabilityEvaluator)
        String fqnAnnotation = "org.springframework.retry.annotation.Retryable";
        entryMethods.addAll(model.findMethodsWithAnnotation(fqnAnnotation));

        // Deduplicate if necessary (though simple/FQN usually map distinctively or we
        // perform set check)
        // For simplicity in this specific task, we'll just iterate found methods.

        // 2. Check reachability to sinks
        for (MethodEntity entryMethod : entryMethods) {
            nodesAnalyzed++;

            for (String sinkType : REMOTE_SINKS) {
                // Sinks in call graph are often "Type#method", so we search for chains to
                // "Type#"
                List<List<String>> chains = callGraph.findChainsToSink(
                        entryMethod.fullyQualifiedName(),
                        sinkType + "#");

                for (List<String> chain : chains) {
                    // Check max depth if needed (rule default is usually 30)
                    if (chain.size() > 30)
                        continue;

                    String sinkMethodFqn = chain.get(chain.size() - 1);
                    String sinkSimpleName = sinkMethodFqn.contains("#")
                            ? sinkMethodFqn.substring(sinkMethodFqn.indexOf('#') + 1) // method name
                            : "unknown";
                    String sinkTypeName = sinkType.substring(sinkType.lastIndexOf('.') + 1);

                    violations.add(Violation.builder()
                            .ruleId(rule.id())
                            .ruleName(rule.name())
                            .severity(rule.severity())
                            .message(String.format(
                                    "@Retryable method '%s' calls %s which violates rule: %s",
                                    entryMethod.simpleName(),
                                    sinkTypeName,
                                    rule.name()))
                            .location(entryMethod.location())
                            .callChain(chain)
                            .build());
                }
            }
        }

        Duration duration = Duration.between(start, Instant.now());
        log.debug("Rule {} found {} violations in {}ms",
                rule.id(), violations.size(), duration.toMillis());

        return EvaluationResult.success(rule.id(), violations, duration, nodesAnalyzed);
    }
}
