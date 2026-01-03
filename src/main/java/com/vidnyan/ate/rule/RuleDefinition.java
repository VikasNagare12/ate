package com.vidnyan.ate.rule;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

import java.util.List;

/**
 * Represents a declarative architectural rule definition.
 * <p>
 * This class serves as the canonical contract for compliance policies.
 * It decouples the "WHAT" (policy definition) from the "HOW" (evaluation
 * logic),
 * enabling rules to be defined in a technology-agnostic JSON format.
 * </p>
 * 
 * @see com.vidnyan.ate.rule.evaluator.RuleEvaluator
 */
@Value
@Builder
@Jacksonized
@JsonIgnoreProperties(ignoreUnknown = true)
public class RuleDefinition {

    // -- Identity & Metadata -- //

    @JsonProperty("id")
    String id;

    @JsonProperty("name")
    String name;

    @JsonProperty("enabled")
    @Builder.Default
    boolean enabled = true;

    @JsonProperty("severity")
    Severity severity;

    @JsonProperty("confidence")
    Confidence confidence;

    // -- Policy Definition -- //

    @JsonProperty("description")
    String description;

    @JsonProperty("target")
    Target target;

    @JsonProperty("constraints")
    Constraints constraints;
    
    // -- Context & Remediation -- //

    @JsonProperty("rationale")
    Rationale rationale;

    @JsonProperty("remediation")
    Remediation remediation;

    @JsonProperty("tags")
    List<String> tags;

    /**
     * Defines the scope where the rule applies.
     */
    @Value
    @Builder
    @Jacksonized
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Target {
        @JsonProperty("type")
        TargetType type;

        @JsonProperty("annotation")
        String annotation; // Post-condition: Valid only if type is ANNOTATED_METHOD or ANNOTATED_TYPE

        @JsonProperty("namePattern")
        String namePattern; // Post-condition: Valid only if type is NAME_PATTERN

        @JsonProperty("packagePattern")
        String packagePattern; // Post-condition: Valid only if type is PACKAGE
    }

    public enum TargetType {
        ANNOTATED_METHOD,
        ANNOTATED_TYPE,
        NAME_PATTERN,
        PACKAGE,
        ALL_METHODS
    }

    /**
     * Defines the invariants that must hold true for the target.
     */
    @Value
    @Builder
    @Jacksonized
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Constraints {
        // Interaction Constraints
        List<String> mustNotInvokeAnnotatedMethods;
        List<String> mustInvokeAnnotatedMethods;
        List<String> mustNotInvokeMethodsMatching;
        List<String> mustInvokeMethodsMatching;

        // Structural Constraints
        List<String> mustNotDependOnPackages;
        List<String> mustDependOnPackages;

        // Metric Constraints
        Integer maxCallDepth;
        Integer maxTransactionDepth;
    }

    /**
     * Contextual information explaining the "Why" behind the rule.
     * Crucial for developer buy-in and understanding.
     */
    @Value
    @Builder
    @Jacksonized
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Rationale {
        String why;
        String impact;
    }

    /**
     * Actionable guidance for resolving violations.
     */
    @Value
    @Builder
    @Jacksonized
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Remediation {
        String summary;
        List<String> options;
    }

    public enum Confidence {
        HIGH, // Deterministic detection, near-zero false positives
        MEDIUM, // Heuristic detection, requires human review
        LOW // Experimental or highly contextual
    }
}

