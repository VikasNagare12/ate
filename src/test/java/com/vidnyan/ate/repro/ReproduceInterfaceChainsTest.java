package com.vidnyan.ate.repro;

import com.vidnyan.ate.adapter.out.evaluator.TransactionBoundaryEvaluatorV2;
import com.vidnyan.ate.adapter.out.parser.JavaParserAdapterV2;
import com.vidnyan.ate.application.port.out.SourceCodeParser;
import com.vidnyan.ate.domain.graph.CallGraph;
import com.vidnyan.ate.domain.rule.EvaluationContext;
import com.vidnyan.ate.domain.rule.EvaluationResult;
import com.vidnyan.ate.domain.rule.RuleDefinition;
import com.vidnyan.ate.domain.rule.Violation;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class ReproduceInterfaceChainsTest {

    @Test
    public void testInterfaceChainViolation() {
        JavaParserAdapterV2 parser = new JavaParserAdapterV2();
        Path sourceRoot = Path.of("src/test/java/com/vidnyan/ate/repro");
        
        SourceCodeParser.ParsingResult result = parser.parse(sourceRoot, 
            new SourceCodeParser.ParsingOptions(true, true, List.of("Reproduce")));
            
        CallGraph callGraph = CallGraph.build(result.sourceModel(), result.callEdges());
        
        System.out.println("Graph Stats: " + callGraph.stats());
        
        RuleDefinition rule = new RuleDefinition(
            "TX-BOUNDARY-001",
            "No Remote Calls Inside Transaction",
            "desc",
            RuleDefinition.Severity.BLOCKER,
            RuleDefinition.Category.TRANSACTION_SAFETY,
            new RuleDefinition.Detection(
                new RuleDefinition.Detection.EntryPoints(
                    List.of("org.springframework.transaction.annotation.Transactional"),
                    List.of(), List.of()
                ),
                new RuleDefinition.Detection.Sinks(
                    List.of(),
                    List.of("org.springframework.web.client.RestTemplate"),
                    List.of()
                ),
                new RuleDefinition.Detection.PathConstraints(List.of(), List.of(), 30)
            ),
            null,
            Map.of(),
            true
        );
        
        TransactionBoundaryEvaluatorV2 evaluator = new TransactionBoundaryEvaluatorV2();
        EvaluationContext context = EvaluationContext.of(rule, result.sourceModel(), callGraph, null);
        EvaluationResult evalResult = evaluator.evaluate(context);
        
        boolean found = false;
        for (Violation v : evalResult.violations()) {
            System.out.println("VIOLATION: " + v.message());
            System.out.println("CHAIN: " + v.callChain());
            if (v.location().filePath().endsWith("TestCaller.java")) {
                found = true;
            }
        }
        
        if (!found) {
            System.err.println("FAILED to detect violation through interface!");
        }
        
        assertTrue(found, "Should find violation even through interface call");
    }
}
