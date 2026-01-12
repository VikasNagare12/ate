package com.vidnyan.ate.repro;

import com.vidnyan.ate.adapter.out.evaluator.TransactionBoundaryEvaluatorV2;
import com.vidnyan.ate.adapter.out.parser.JavaParserAdapterV2;
import com.vidnyan.ate.application.port.out.SourceCodeParser;
import com.vidnyan.ate.domain.graph.CallGraph;
import com.vidnyan.ate.domain.rule.*;
import org.junit.jupiter.api.Test;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class ReproduceTxBoundaryIssueTest {

    @Test
    public void testDeepChainViolation() {
        // 1. Setup Parser
        JavaParserAdapterV2 parser = new JavaParserAdapterV2();
        Path sourceRoot = Path.of("src/main/java");

        SourceCodeParser.ParsingResult result = parser.parse(sourceRoot,
                new SourceCodeParser.ParsingOptions(true, true, List.of()));

        // 2. Build Graph
        CallGraph callGraph = CallGraph.build(result.sourceModel(), result.callEdges());

        System.out.println("Graph Stats: " + callGraph.stats());

        // 4. Setup Rule
        RuleDefinition rule = new RuleDefinition(
                "TX-BOUNDARY-001",
                "No Remote Calls Inside Transaction",
                "desc",
                RuleDefinition.Severity.BLOCKER,
                RuleDefinition.Category.TRANSACTION_SAFETY,
                new RuleDefinition.Detection(
                        new RuleDefinition.Detection.EntryPoints(
                                List.of("org.springframework.transaction.annotation.Transactional"),
                                List.of(), List.of()),
                        new RuleDefinition.Detection.Sinks(
                                List.of(),
                                List.of(
                                        "org.springframework.web.client.RestTemplate",
                                        "org.springframework.web.reactive.function.client.WebClient",
                                        "java.net.http.HttpClient"),
                                List.of()),
                        new RuleDefinition.Detection.PathConstraints(List.of(), List.of(), 30)),
                null,
                Map.of(),
                true);

        // 5. Evaluate
        TransactionBoundaryEvaluatorV2 evaluator = new TransactionBoundaryEvaluatorV2();
        EvaluationContext context = EvaluationContext.of(rule, result.sourceModel(), callGraph, null);
        EvaluationResult evalResult = evaluator.evaluate(context);

        // 6. Assertions
        boolean foundDeepChain = false;
        boolean foundIndirect = false;

        for (Violation v : evalResult.violations()) {
            System.out.println("VIOLATION: " + v.message());
            System.out.println("CHAIN: " + v.callChain());

            if (v.location().filePath().endsWith("TransactionBoundaryTestService.java")) {
                String chainStart = v.callChain().isEmpty() ? "" : v.callChain().get(0);
                if (chainStart.contains("deepChainViolation")) {
                    foundDeepChain = true;
                }
                if (chainStart.contains("indirectViolation")) {
                    foundIndirect = true;
                }
            }
        }

        if (!foundDeepChain) {
            System.err.println("FAILED to detect deepChainViolation!");
        }
        if (!foundIndirect) {
            System.err.println("FAILED to detect indirectViolation!");
        }

        assertTrue(foundDeepChain, "Should find deepChainViolation");
        assertTrue(foundIndirect, "Should find indirectViolation");
    }
}
