package com.vidnyan.ate.report;

import com.vidnyan.ate.rule.Severity;
import com.vidnyan.ate.rule.Violation;
import lombok.Builder;
import lombok.Value;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Report Model - final output of the analysis.
 * Contains violations grouped by severity with summary statistics.
 */
@Value
@Builder
public class ReportModel {
    Summary summary;
    List<Violation> violations;
    
    @Value
    @Builder
    public static class Summary {
        int totalViolations;
        Map<Severity, Integer> violationsBySeverity;
        int blockerCount;
        int errorCount;
        int warnCount;
        int infoCount;
    }
    
    /**
     * Build report from violations.
     */
    public static ReportModel build(List<Violation> violations) {
        // Deduplicate by fingerprint
        Map<String, Violation> uniqueViolations = violations.stream()
                .collect(Collectors.toMap(
                        Violation::getFingerprint,
                        v -> v,
                        (v1, v2) -> v1 // Keep first if duplicate
                ));
        
        List<Violation> deduplicated = uniqueViolations.values().stream()
                .sorted((v1, v2) -> {
                    // Sort by severity (BLOCKER first), then by file, then by line
                    int severityCompare = v1.getSeverity().compareTo(v2.getSeverity());
                    if (severityCompare != 0) return severityCompare;
                    
                    int fileCompare = v1.getLocation().getFilePath()
                            .compareTo(v2.getLocation().getFilePath());
                    if (fileCompare != 0) return fileCompare;
                    
                    return Integer.compare(v1.getLocation().getLine(), v2.getLocation().getLine());
                })
                .toList();
        
        // Build summary
        Map<Severity, Integer> bySeverity = deduplicated.stream()
                .collect(Collectors.groupingBy(
                        Violation::getSeverity,
                        Collectors.collectingAndThen(Collectors.counting(), Long::intValue)
                ));
        
        Summary summary = Summary.builder()
                .totalViolations(deduplicated.size())
                .violationsBySeverity(bySeverity)
                .blockerCount(bySeverity.getOrDefault(Severity.BLOCKER, 0))
                .errorCount(bySeverity.getOrDefault(Severity.ERROR, 0))
                .warnCount(bySeverity.getOrDefault(Severity.WARN, 0))
                .infoCount(bySeverity.getOrDefault(Severity.INFO, 0))
                .build();
        
        return ReportModel.builder()
                .summary(summary)
                .violations(deduplicated)
                .build();
    }
    
    /**
     * Determine if analysis should PASS or FAIL.
     */
    public AnalysisResult getResult() {
        if (summary.getBlockerCount() > 0) {
            return AnalysisResult.FAIL;
        }
        // TODO: Add configurable thresholds for ERROR and WARN
        return AnalysisResult.PASS;
    }
    
    public enum AnalysisResult {
        PASS,
        FAIL,
        WARN
    }
}

