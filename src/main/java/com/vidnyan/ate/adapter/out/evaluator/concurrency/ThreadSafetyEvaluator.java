package com.vidnyan.ate.adapter.out.evaluator.concurrency;

import com.vidnyan.ate.analyzer.SootUpEvaluationContext;
import com.vidnyan.ate.domain.model.Location;
import com.vidnyan.ate.domain.rule.EvaluationResult;
import com.vidnyan.ate.domain.rule.RuleDefinition;
import com.vidnyan.ate.domain.rule.RuleEvaluator;
import com.vidnyan.ate.domain.rule.Violation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import sootup.core.jimple.common.stmt.Stmt;
import sootup.core.model.SootMethod;
import sootup.java.core.JavaSootClass;
import sootup.java.core.JavaSootMethod;
import sootup.java.core.views.JavaView;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
public class ThreadSafetyEvaluator implements RuleEvaluator {

    @Override
    public boolean supports(RuleDefinition rule) {
        return "THREAD-MGMT-001".equals(rule.id());
    }

    @Override
    public EvaluationResult evaluate(SootUpEvaluationContext context) {
        Instant start = Instant.now();
        List<Violation> violations = new ArrayList<>();
        int checked = 0;
        JavaView view = (JavaView) context.view();

        for (JavaSootClass clazz : view.getClasses().stream().map(c -> (JavaSootClass) c).toList()) {
            for (SootMethod method : clazz.getMethods()) {
                if (((JavaSootMethod) method).hasBody()) {
                    checked++;
                    checkThreadUsage(method, violations, context.rule());
                }
            }
        }

        return EvaluationResult.success(context.rule().id(), violations, Duration.between(start, Instant.now()),
                checked);
    }

    private void checkThreadUsage(SootMethod method, List<Violation> violations, RuleDefinition rule) {
        try {
            for (Stmt stmt : method.getBody().getStmtGraph().getStmts()) {
                if (stmt.containsInvokeExpr()) {
                    // Corrected API: getDeclClassType() on MethodSignature
                    String declaringClass = stmt.getInvokeExpr().getMethodSignature().getDeclClassType().toString();
                    String methodName = stmt.getInvokeExpr().getMethodSignature().getName();

                    // Check for java.lang.Thread usage
                    if ("java.lang.Thread".equals(declaringClass)) {
                        if ("<init>".equals(methodName) || "start".equals(methodName)) {
                            int line = 0;
                            violations.add(Violation.builder()
                                    .ruleId(rule.id())
                                    .message("Direct usage of java.lang.Thread (" + methodName
                                            + ") detected. Use Spring @Async or TaskExecutor.")
                                    .callChain(
                                            List.of(method.getDeclaringClassType().toString() + "." + method.getName()))
                                    .location(Location.at(
                                            method.getDeclaringClassType().toString() + "." + method.getName(), line,
                                            0))
                                    .build());
                        }
                    }
                }
            }
        } catch (Exception e) {
            // Swallow specific body read errors
        }
    }
}
