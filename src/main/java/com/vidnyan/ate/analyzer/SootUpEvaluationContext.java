package com.vidnyan.ate.analyzer;

import com.vidnyan.ate.domain.rule.RuleDefinition;
import sootup.core.views.View;

public record SootUpEvaluationContext(
    View view,
    RuleDefinition rule
) {}
