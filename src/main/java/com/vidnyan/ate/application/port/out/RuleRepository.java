package com.vidnyan.ate.application.port.out;

import com.vidnyan.ate.domain.rule.RuleDefinition;

import java.util.List;
import java.util.Optional;

/**
 * Port for loading rule definitions.
 * Implemented by adapters that read from files, databases, etc.
 */
public interface RuleRepository {
    
    /**
     * Load all available rules.
     */
    List<RuleDefinition> findAll();
    
    /**
     * Load a specific rule by ID.
     */
    Optional<RuleDefinition> findById(String ruleId);
    
    /**
     * Load rules by category.
     */
    List<RuleDefinition> findByCategory(RuleDefinition.Category category);
    
    /**
     * Load enabled rules only.
     */
    List<RuleDefinition> findEnabled();
}
