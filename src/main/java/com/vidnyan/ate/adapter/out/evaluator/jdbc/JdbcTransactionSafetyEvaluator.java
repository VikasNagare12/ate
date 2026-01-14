package com.vidnyan.ate.adapter.out.evaluator.jdbc;

import com.vidnyan.ate.analyzer.SootUpEvaluationContext;
import com.vidnyan.ate.domain.model.Location;
import com.vidnyan.ate.domain.rule.EvaluationResult;
import com.vidnyan.ate.domain.rule.RuleDefinition;
import com.vidnyan.ate.domain.rule.RuleEvaluator;
import com.vidnyan.ate.domain.rule.Violation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import sootup.core.jimple.common.expr.AbstractInvokeExpr;
import sootup.core.jimple.common.stmt.Stmt;
import sootup.core.model.SootMethod;
import sootup.core.signatures.MethodSignature;
import sootup.java.core.JavaSootClass;
import sootup.java.core.JavaSootMethod;
import sootup.java.core.views.JavaView;

import java.time.Duration;
import java.time.Instant;
import java.util.*;

@Slf4j
@Component
public class JdbcTransactionSafetyEvaluator implements RuleEvaluator {

    private static final Set<String> JDBC_SINKS = Set.of(
            "javax.persistence.EntityManager",
            "java.sql.Connection",
            "org.springframework.jdbc.core.JdbcTemplate",
            "org.springframework.data.jpa.repository.JpaRepository");

    @Override
    public boolean supports(RuleDefinition rule) {
        return "JDBC-TX-001".equals(rule.id());
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
                    checkMethod(method, view, violations, context.rule());
                }
            }
        }

        return EvaluationResult.success(context.rule().id(), violations, Duration.between(start, Instant.now()), checked);
    }

    private void checkMethod(SootMethod entryMethod, JavaView view, List<Violation> violations, RuleDefinition rule) {
        // JDBC-TX-001: JDBC operations must be inside @Transactional
        // If method has @Transactional (or class has it), it's safe.
        // If NOT, we must ensure it doesn't call JDBC sinks (directly or indirectly).

        boolean hasTx = hasAnnotation(entryMethod, view, "Transactional");
        if (!hasTx) {
            hasTx = hasClassAnnotation(entryMethod.getDeclaringClassType().toString(), view, "Transactional");
        }

        if (hasTx) {
            return; // Safe because wrapped in TX
        }

        // No Transaction: Check if it uses JDBC
        List<String> callChain = new ArrayList<>();
        callChain.add(entryMethod.getDeclaringClassType().toString() + "." + entryMethod.getName());

        boolean usesJdbcSink = traverseForSinkUsage(entryMethod.getSignature(), view, JDBC_SINKS, new HashSet<>(), callChain, 0);

        if (usesJdbcSink) {
             violations.add(Violation.builder()
                    .ruleId(rule.id())
                    .message("JDBC/JPA operation detected outside of @Transactional context")
                    .callChain(callChain)
                    .location(Location.at(
                            entryMethod.getDeclaringClassType().toString() + "." + entryMethod.getName(), 0, 0))
                    .build());
        }
    }

    /**
     * Recursively traverse method calls to detect if any sink type is used.
     */
    private boolean traverseForSinkUsage(MethodSignature currentMethodSig, JavaView view, Set<String> sinks,
                                         Set<MethodSignature> visited, List<String> callChain, int depth) {
        if (depth > 10 || visited.contains(currentMethodSig)) {
            return false;
        }

        Optional<? extends SootMethod> optMethod = view.getMethod(currentMethodSig);
        if (optMethod.isEmpty() || !((JavaSootMethod) optMethod.get()).hasBody()) {
            return false;
        }

        visited.add(currentMethodSig);
        JavaSootMethod method = (JavaSootMethod) optMethod.get();

        try {
            for (Stmt stmt : method.getBody().getStmtGraph().getStmts()) {
                if (stmt.containsInvokeExpr()) {
                    AbstractInvokeExpr invokeExpr = stmt.getInvokeExpr();
                    String declaringClass = invokeExpr.getMethodSignature().getDeclClassType().toString();

                    // Check if this is a direct sink call
                    if (sinks.stream().anyMatch(declaringClass::contains)) {
                        callChain.add(declaringClass + "." + invokeExpr.getMethodSignature().getName());
                        return true;
                    }

                    // Recurse into called method
                    MethodSignature targetSig = invokeExpr.getMethodSignature();
                    if (view.getMethod(targetSig).isPresent()) {
                        List<String> newChain = new ArrayList<>(callChain);
                        newChain.add(targetSig.getDeclClassType().toString() + "." + targetSig.getName());
                        if (traverseForSinkUsage(targetSig, view, sinks, visited, newChain, depth + 1)) {
                            callChain.clear();
                            callChain.addAll(newChain);
                            return true;
                        }
                    }
                }
            }
        } catch (Exception e) {
            // Ignore body read errors
        }

        visited.remove(currentMethodSig);
        return false;
    }

    private boolean hasAnnotation(SootMethod method, JavaView view, String annotationFragment) {
        if (method instanceof JavaSootMethod javaMethod) {
            try {
                for (sootup.java.core.AnnotationUsage a : javaMethod.getAnnotations(Optional.of(view))) {
                    if (a.toString().contains(annotationFragment)) {
                        return true;
                    }
                }
            } catch (Exception e) {
                return false;
            }
        }
        return false;
    }

    private boolean hasClassAnnotation(String mainClassName, JavaView view, String annotationFragment) {
        Optional<JavaSootClass> optClass = view
                .getClass(view.getIdentifierFactory().getClassType(mainClassName))
                .map(c -> (JavaSootClass) c);
        if (optClass.isPresent()) {
            try {
                for (sootup.java.core.AnnotationUsage a : optClass.get().getAnnotations(Optional.of(view))) {
                    if (a.toString().contains(annotationFragment)) {
                        return true;
                    }
                }
            } catch (Exception e) {
                return false;
            }
        }
        return false;
    }
}
