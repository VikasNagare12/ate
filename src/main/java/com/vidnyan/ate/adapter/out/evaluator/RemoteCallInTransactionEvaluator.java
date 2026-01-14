package com.vidnyan.ate.adapter.out.evaluator;

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
public class RemoteCallInTransactionEvaluator implements RuleEvaluator {

    private static final Set<String> REMOTE_TYPES = Set.of(
            "org.springframework.web.client.RestTemplate",
            "org.springframework.web.reactive.function.client.WebClient",
            "java.net.http.HttpClient",
            "org.apache.http.client.HttpClient",
            "java.net.HttpURLConnection");

    @Override
    public boolean supports(RuleDefinition rule) {
        return "TX-BOUNDARY-001".equals(rule.id());
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
                    if (hasAnnotation(method, view, "Transactional")) {
                        checked++;
                        checkRemoteCalls(method, view, violations, context.rule());
                    }
                }
            }
        }

        return EvaluationResult.success(context.rule().id(), violations, Duration.between(start, Instant.now()), checked);
    }

    private void checkRemoteCalls(SootMethod entryMethod, JavaView view, List<Violation> violations,
                                  RuleDefinition rule) {
        Queue<MethodSignature> queue = new LinkedList<>();
        Set<MethodSignature> visited = new HashSet<>();
        Map<MethodSignature, List<String>> paths = new HashMap<>();

        queue.add(entryMethod.getSignature());
        visited.add(entryMethod.getSignature());
        paths.put(entryMethod.getSignature(),
                new ArrayList<>(List.of(entryMethod.getDeclaringClassType().toString() + "." + entryMethod.getName())));

        while (!queue.isEmpty()) {
            MethodSignature currentSig = queue.poll();
            List<String> currentPath = paths.get(currentSig);

            if (currentPath.size() > 10)
                continue;

            Optional<? extends SootMethod> targetOpt = view.getMethod(currentSig);
            if (targetOpt.isEmpty() || !((JavaSootMethod) targetOpt.get()).hasBody())
                continue;

            JavaSootMethod method = (JavaSootMethod) targetOpt.get();
            try {
                for (Stmt stmt : method.getBody().getStmtGraph().getStmts()) {
                    if (stmt.containsInvokeExpr()) {
                        AbstractInvokeExpr invoke = stmt.getInvokeExpr();

                        if (isRemoteCall(invoke, view)) {
                            violations.add(Violation.builder()
                                    .ruleId(rule.id())
                                    .message("Remote call detected inside @Transactional")
                                    .callChain(append(currentPath,
                                            invoke.getMethodSignature().getDeclClassType().toString() + "."
                                                    + invoke.getMethodSignature().getName()))
                                    .location(Location.at(
                                            entryMethod.getDeclaringClassType().toString() + "." + entryMethod.getName(), 0, 0))
                                    .build());
                            return;
                        }

                        MethodSignature targetSig = invoke.getMethodSignature();
                        if (visited.add(targetSig)) {
                            if (view.getMethod(targetSig).isPresent()) {
                                queue.add(targetSig);
                                paths.put(targetSig, append(currentPath,
                                        targetSig.getDeclClassType().toString() + "." + targetSig.getName()));
                            }
                        }
                    }
                }
            } catch (Exception e) {
                // Ignore body read errors
            }
        }
    }

    private boolean isRemoteCall(AbstractInvokeExpr invoke, JavaView view) {
        String className = invoke.getMethodSignature().getDeclClassType().toString();

        if (REMOTE_TYPES.stream().anyMatch(className::equals)) {
            return true;
        }

        // Check for @FeignClient on the interface
        Optional<JavaSootClass> optClass = view.getClass(view.getIdentifierFactory().getClassType(className))
                .map(c -> (JavaSootClass) c);
        if (optClass.isPresent()) {
            return hasClassAnnotation(optClass.get(), view, "FeignClient");
        }

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

    private boolean hasClassAnnotation(JavaSootClass clazz, JavaView view, String annotationFragment) {
        try {
            for (sootup.java.core.AnnotationUsage a : clazz.getAnnotations(Optional.of(view))) {
                if (a.toString().contains(annotationFragment)) {
                    return true;
                }
            }
        } catch (Exception e) {
            return false;
        }
        return false;
    }

    private List<String> append(List<String> list, String item) {
        List<String> newL = new ArrayList<>(list);
        newL.add(item);
        return newL;
    }
}
