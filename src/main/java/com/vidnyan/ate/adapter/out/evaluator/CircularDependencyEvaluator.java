package com.vidnyan.ate.adapter.out.evaluator;

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
import java.util.*;

@Slf4j
@Component
public class CircularDependencyEvaluator implements RuleEvaluator {

    @Override
    public boolean supports(RuleDefinition rule) {
        return "CIRCULAR-DEP-001".equals(rule.id());
    }

    @Override
    public EvaluationResult evaluate(SootUpEvaluationContext context) {
        Instant start = Instant.now();
        List<Violation> violations = new ArrayList<>();
        JavaView view = (JavaView) context.view();

        // 1. Build Package Graph
        Map<String, Set<String>> packageDeps = new HashMap<>();
        List<JavaSootClass> classes = view.getClasses().stream().map(c -> (JavaSootClass) c).toList();

        for (JavaSootClass clazz : classes) {
            String sourcePkg = getPackageName(clazz.getType().toString());
            packageDeps.putIfAbsent(sourcePkg, new HashSet<>());

            for (SootMethod method : clazz.getMethods()) {
                if (((JavaSootMethod) method).hasBody()) {
                    try {
                        for (Stmt stmt : method.getBody().getStmtGraph().getStmts()) {
                            if (stmt.containsInvokeExpr()) {
                                // Corrected API: getDeclClassType() on MethodSignature
                                String targetClass = stmt.getInvokeExpr().getMethodSignature().getDeclClassType()
                                        .toString();
                                String targetPkg = getPackageName(targetClass);

                                if (!sourcePkg.equals(targetPkg) && isAppPackage(targetPkg)) {
                                    packageDeps.get(sourcePkg).add(targetPkg);
                                }
                            }
                        }
                    } catch (Exception e) {
                        /* ignore */ }
                }
            }
        }

        // 2. Detect Cycles
        List<List<String>> cycles = detectCycles(packageDeps);

        for (List<String> cycle : cycles) {
            violations.add(Violation.builder()
                    .ruleId(context.rule().id())
                    .message("Circular package dependency detected: " + String.join(" -> ", cycle))
                    .callChain(cycle)
                    .location(Location.at(cycle.get(0), 0, 0))
                    .build());
        }

        return EvaluationResult.success(context.rule().id(), violations, Duration.between(start, Instant.now()),
                classes.size());
    }

    private boolean isAppPackage(String pkg) {
        return !pkg.startsWith("java.") && !pkg.startsWith("javax.") && !pkg.startsWith("org.springframework.")
                && !pkg.isEmpty();
    }

    private String getPackageName(String className) {
        int lastDot = className.lastIndexOf('.');
        return lastDot > 0 ? className.substring(0, lastDot) : "";
    }

    private List<List<String>> detectCycles(Map<String, Set<String>> graph) {
        List<List<String>> cycles = new ArrayList<>();
        Set<String> visited = new HashSet<>();
        Set<String> recursionStack = new HashSet<>();

        for (String node : graph.keySet()) {
            if (!visited.contains(node)) {
                dfs(node, graph, visited, recursionStack, cycles, new ArrayList<>());
            }
        }
        return cycles;
    }

    private void dfs(String node, Map<String, Set<String>> graph, Set<String> visited,
            Set<String> recursionStack, List<List<String>> cycles, List<String> path) {
        visited.add(node);
        recursionStack.add(node);
        path.add(node);

        if (graph.containsKey(node)) {
            for (String neighbor : graph.get(node)) {
                if (!visited.contains(neighbor)) {
                    dfs(neighbor, graph, visited, recursionStack, cycles, new ArrayList<>(path));
                } else if (recursionStack.contains(neighbor)) {
                    List<String> cycle = new ArrayList<>();
                    int startIndex = path.indexOf(neighbor);
                    if (startIndex != -1) {
                        cycle.addAll(path.subList(startIndex, path.size()));
                        cycle.add(neighbor);
                        cycles.add(cycle);
                    }
                }
            }
        }

        recursionStack.remove(node);
    }
}
