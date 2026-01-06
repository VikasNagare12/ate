package com.vidnyan.ate.application.port.out;

import com.vidnyan.ate.domain.graph.CallEdge;
import com.vidnyan.ate.domain.model.SourceModel;

import java.nio.file.Path;
import java.util.List;

/**
 * Port for parsing source code into the domain model.
 * Implemented by adapters (e.g., JavaParser adapter).
 */
public interface SourceCodeParser {
    
    /**
     * Parse source files and build the source model.
     * @param sourcePath Root directory of source files
     * @param options Parsing options
     * @return Parsing result with source model and call edges
     */
    ParsingResult parse(Path sourcePath, ParsingOptions options);
    
    /**
     * Parsing options.
     */
    record ParsingOptions(
        boolean includeTests,
        boolean resolveSymbols,
        List<String> excludePatterns
    ) {
        public static ParsingOptions defaults() {
            return new ParsingOptions(false, true, List.of());
        }
    }
    
    /**
     * Parsing result.
     */
    record ParsingResult(
        SourceModel sourceModel,
        List<CallEdge> callEdges,
        ParsingStats stats
    ) {}
    
    /**
     * Parsing statistics.
     */
    record ParsingStats(
        int filesProcessed,
        int typesExtracted,
        int methodsExtracted,
        int fieldsExtracted,
        int callsExtracted,
        long durationMs
    ) {}
}
