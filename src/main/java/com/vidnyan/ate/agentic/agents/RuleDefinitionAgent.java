package com.vidnyan.ate.agentic.agents;

import com.vidnyan.ate.agentic.core.Agent;
import com.vidnyan.ate.application.port.out.RuleRepository;
import com.vidnyan.ate.domain.rule.RuleDefinition;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Agent responsible for loading and interpreting architectural rules.
 * "The Lawmaker" - Fetches constraints the codebase must adhere to.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RuleDefinitionAgent implements Agent<RuleDefinitionAgent.Input, RuleDefinitionAgent.Output> {

    private final RuleRepository ruleRepository;

    @Override
    public String getName() {
        return "RuleDefinitionAgent";
    }

    @Override
    public Output execute(Input input) {
        log.info("[{}] Loading architectural rules...", getName());

        List<RuleDefinition> allRules = ruleRepository.findEnabled();

        List<RuleDefinition> activeRules;
        if (input.ruleIds() == null || input.ruleIds().isEmpty()) {
            activeRules = allRules;
        } else {
            activeRules = input.ruleIds().stream()
                    .map(ruleRepository::findById)
                    .filter(java.util.Optional::isPresent)
                    .map(java.util.Optional::get)
                    .toList();
        }

        log.info("[{}] Loaded {} active rules.", getName(), activeRules.size());

        return new Output(activeRules);
    }

    public record Input(List<String> ruleIds) {
        public static Input all() {
            return new Input(null);
        }
    }

    public record Output(List<RuleDefinition> rules) {
    }
}
