package com.vidnyan.ate.adapter.out.rule;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vidnyan.ate.application.port.out.RuleRepository;
import com.vidnyan.ate.domain.rule.RuleDefinition;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * File system based rule repository.
 * Loads rules from JSON files in the classpath.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FileSystemRuleRepository implements RuleRepository {
    
    private final ObjectMapper objectMapper;
    
    @Value("${ate.rules.path:classpath*:rules/*.json}")
    private String rulesPath;
    
    private final Map<String, RuleDefinition> rules = new ConcurrentHashMap<>();
    
    @PostConstruct
    public void loadRules() {
        try {
            PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
            Resource[] resources = resolver.getResources(rulesPath);
            
            for (Resource resource : resources) {
                try {
                    RuleDto dto = objectMapper.readValue(resource.getInputStream(), RuleDto.class);
                    RuleDefinition rule = mapToRule(dto);
                    rules.put(rule.id(), rule);
                    log.info("Loaded rule: {} - {}", rule.id(), rule.name());
                } catch (Exception e) {
                    log.warn("Failed to load rule from {}: {}", resource.getFilename(), e.getMessage());
                }
            }
            
            log.info("Loaded {} rules from {}", rules.size(), rulesPath);
        } catch (IOException e) {
            log.error("Failed to load rules", e);
        }
    }
    
    @Override
    public List<RuleDefinition> findAll() {
        return List.copyOf(rules.values());
    }
    
    @Override
    public Optional<RuleDefinition> findById(String ruleId) {
        return Optional.ofNullable(rules.get(ruleId));
    }
    
    @Override
    public List<RuleDefinition> findByCategory(RuleDefinition.Category category) {
        return rules.values().stream()
                .filter(r -> r.category() == category)
                .toList();
    }
    
    @Override
    public List<RuleDefinition> findEnabled() {
        return rules.values().stream()
                .filter(RuleDefinition::isEnabled)
                .toList();
    }
    
    private RuleDefinition mapToRule(RuleDto dto) {
        return RuleDefinition.builder()
                .id(dto.id)
                .name(dto.name)
                .description(dto.description)
                .severity(mapSeverity(dto.severity))
                .category(mapCategory(dto.category))
                .detection(mapDetection(dto.detection))
                .remediation(mapRemediation(dto.remediation))
                .config(dto.config != null ? dto.config : Map.of())
                .isEnabled(dto.isEnabled != null ? dto.isEnabled : true)
                .build();
    }
    
    private RuleDefinition.Severity mapSeverity(String severity) {
        if (severity == null) return RuleDefinition.Severity.ERROR;
        return switch (severity.toUpperCase()) {
            case "BLOCKER" -> RuleDefinition.Severity.BLOCKER;
            case "ERROR" -> RuleDefinition.Severity.ERROR;
            case "WARN", "WARNING" -> RuleDefinition.Severity.WARN;
            case "INFO" -> RuleDefinition.Severity.INFO;
            default -> RuleDefinition.Severity.ERROR;
        };
    }
    
    private RuleDefinition.Category mapCategory(String category) {
        if (category == null) return RuleDefinition.Category.CUSTOM;
        return switch (category.toUpperCase().replace("-", "_").replace(" ", "_")) {
            case "TRANSACTION_SAFETY" -> RuleDefinition.Category.TRANSACTION_SAFETY;
            case "ASYNC_SAFETY" -> RuleDefinition.Category.ASYNC_SAFETY;
            case "RETRY_SAFETY" -> RuleDefinition.Category.RETRY_SAFETY;
            case "CIRCULAR_DEPENDENCY" -> RuleDefinition.Category.CIRCULAR_DEPENDENCY;
            case "LAYERED_ARCHITECTURE" -> RuleDefinition.Category.LAYERED_ARCHITECTURE;
            case "SECURITY" -> RuleDefinition.Category.SECURITY;
            case "PERFORMANCE" -> RuleDefinition.Category.PERFORMANCE;
            default -> RuleDefinition.Category.CUSTOM;
        };
    }
    
    private RuleDefinition.Detection mapDetection(DetectionDto dto) {
        if (dto == null) return null;
        return new RuleDefinition.Detection(
                new RuleDefinition.Detection.EntryPoints(
                        dto.entryPoints != null && dto.entryPoints.annotations != null 
                                ? dto.entryPoints.annotations : List.of(),
                        dto.entryPoints != null && dto.entryPoints.types != null 
                                ? dto.entryPoints.types : List.of(),
                        dto.entryPoints != null && dto.entryPoints.methodPatterns != null 
                                ? dto.entryPoints.methodPatterns : List.of()
                ),
                new RuleDefinition.Detection.Sinks(
                        dto.sinks != null && dto.sinks.annotations != null 
                                ? dto.sinks.annotations : List.of(),
                        dto.sinks != null && dto.sinks.types != null 
                                ? dto.sinks.types : List.of(),
                        dto.sinks != null && dto.sinks.methodPatterns != null 
                                ? dto.sinks.methodPatterns : List.of()
                ),
                new RuleDefinition.Detection.PathConstraints(
                        dto.pathConstraints != null && dto.pathConstraints.mustContain != null 
                                ? dto.pathConstraints.mustContain : List.of(),
                        dto.pathConstraints != null && dto.pathConstraints.mustNotContain != null 
                                ? dto.pathConstraints.mustNotContain : List.of(),
                        dto.pathConstraints != null ? dto.pathConstraints.maxDepth : 100
                )
        );
    }
    
    private RuleDefinition.Remediation mapRemediation(RemediationDto dto) {
        if (dto == null) return null;
        return new RuleDefinition.Remediation(
                dto.quickFix,
                dto.explanation,
                dto.references != null ? dto.references : List.of()
        );
    }
    
    // DTO classes for JSON deserialization
    static class RuleDto {
        public String id;
        public String name;
        public String description;
        public String severity;
        public String category;
        public DetectionDto detection;
        public RemediationDto remediation;
        public Map<String, Object> config;
        public Boolean isEnabled;
    }
    
    static class DetectionDto {
        public EntryPointsDto entryPoints;
        public SinksDto sinks;
        public PathConstraintsDto pathConstraints;
    }
    
    static class EntryPointsDto {
        public List<String> annotations;
        public List<String> types;
        public List<String> methodPatterns;
    }
    
    static class SinksDto {
        public List<String> annotations;
        public List<String> types;
        public List<String> methodPatterns;
    }
    
    static class PathConstraintsDto {
        public List<String> mustContain;
        public List<String> mustNotContain;
        public int maxDepth = 100;
    }
    
    static class RemediationDto {
        public String quickFix;
        public String explanation;
        public List<String> references;
    }
}
