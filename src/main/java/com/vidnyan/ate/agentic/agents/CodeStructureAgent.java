package com.vidnyan.ate.agentic.agents;

import com.vidnyan.ate.adapter.out.parser.JavaParserAdapterV2;
import com.vidnyan.ate.agentic.core.Agent;
import com.vidnyan.ate.application.port.out.SourceCodeParser;
import com.vidnyan.ate.domain.graph.CallGraph;
import com.vidnyan.ate.domain.model.SourceModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.List;

/**
 * Agent responsible for analyzing source code structure.
 * "The Eyes" - Builds the knowledge graph from raw source files.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CodeStructureAgent implements Agent<CodeStructureAgent.Input, CodeStructureAgent.Output> {

    private final JavaParserAdapterV2 parser;

    @Override
    public String getName() {
        return "CodeStructureAgent";
    }

    @Override
    public Output execute(Input input) {
        log.info("[{}] Analyzing source code at: {}", getName(), input.sourcePath());
        
        SourceCodeParser.ParsingResult result = parser.parse(
            input.sourcePath(),
            new SourceCodeParser.ParsingOptions(true, true, List.of())
        );
        
        SourceModel model = result.sourceModel();
        CallGraph graph = CallGraph.build(model, result.callEdges());
        
        log.info("[{}] Analysis complete. Found {} types, {} methods, {} call edges.",
            getName(), model.types().size(), model.methods().size(), result.callEdges().size());
        
        return new Output(model, graph, result.stats());
    }

    public record Input(Path sourcePath) {}
    
    public record Output(
        SourceModel sourceModel,
        CallGraph callGraph,
        SourceCodeParser.ParsingStats stats
    ) {}
}
