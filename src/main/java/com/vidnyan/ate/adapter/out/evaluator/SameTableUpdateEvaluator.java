package com.vidnyan.ate.adapter.out.evaluator;

import com.vidnyan.ate.analyzer.SootUpEvaluationContext;
import com.vidnyan.ate.domain.model.Location;
import com.vidnyan.ate.domain.rule.EvaluationResult;
import com.vidnyan.ate.domain.rule.RuleDefinition;
import com.vidnyan.ate.domain.rule.RuleEvaluator;
import com.vidnyan.ate.domain.rule.Violation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import sootup.core.graph.StmtGraph;
import sootup.core.jimple.common.stmt.Stmt;
import sootup.core.jimple.common.expr.AbstractInvokeExpr;
import sootup.core.jimple.common.constant.StringConstant;
import sootup.core.model.SootMethod;
import sootup.core.signatures.MethodSignature;
import sootup.java.core.JavaSootClass;
import sootup.java.core.JavaSootMethod;
import sootup.java.core.views.JavaView;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Component
public class SameTableUpdateEvaluator implements RuleEvaluator {

    // Regex to capture table name from UPDATE/INSERT
    // Simple regex: matches "UPDATE tableName" or "INSERT INTO tableName"
    private static final Pattern TABLE_NAME_PATTERN = Pattern.compile("(?i)(?:UPDATE|INSERT\\s+INTO)\\s+(\\w+)");

    @Override
    public boolean supports(RuleDefinition rule) {
        return "SAME-TABLE-UPDATE-001".equals(rule.id());
    }

    @Override
    public EvaluationResult evaluate(SootUpEvaluationContext context) {
        Instant start = Instant.now();
        List<Violation> violations = new ArrayList<>();
        int pathsAnalyzed = 0;

        // Start traversal from each class in the view
        for (JavaSootClass clazz : context.view().getClasses().stream().map(c -> (JavaSootClass) c).toList()) {
            for (SootMethod method : clazz.getMethods()) {
                if (isTransactional(method, (JavaView) context.view())) {
                    pathsAnalyzed++;
                    // Per-transaction state
                    Map<String, List<UpdateOperation>> tableUpdates = new HashMap<>();
                    traverseAndCollectUpdates(method.getSignature(), context, new HashSet<>(), tableUpdates, 0,
                            new ArrayList<>());

                    // Analyze findings for this transaction
                    for (Map.Entry<String, List<UpdateOperation>> entry : tableUpdates.entrySet()) {
                        String tableName = entry.getKey();
                        List<UpdateOperation> updates = entry.getValue();

                        // If same table updated more than once, flag violation
                        if (updates.size() > 1 && !"unknown".equals(tableName)) {
                            ViolationsHelper.addViolation(violations, context.rule(), method, updates);
                        }
                    }
                }
            }
        }

        return EvaluationResult.success(context.rule().id(), violations, Duration.between(start, Instant.now()),
                pathsAnalyzed);
    }

    private void traverseAndCollectUpdates(MethodSignature currentMethodSignature, SootUpEvaluationContext context,
            Set<MethodSignature> recursionStack, Map<String, List<UpdateOperation>> tableUpdates, int depth,
            List<String> incomingArgs) {
        if (depth > 10 || recursionStack.contains(currentMethodSignature)) {
            return;
        }

        Optional<JavaSootMethod> optMethod = context.view().getMethod(currentMethodSignature)
                .map(m -> (JavaSootMethod) m);
        if (optMethod.isEmpty() || !optMethod.get().hasBody()) {
            return;
        }

        // Add to stack for cycle detection
        recursionStack.add(currentMethodSignature);

        try {
            JavaSootMethod method = optMethod.get();
            StmtGraph<?> graph = method.getBody().getStmtGraph();
            // Convert Collection to List explicitly
            List<sootup.core.jimple.basic.Local> paramLocals = new ArrayList<>(method.getBody().getParameterLocals());

            // Map to store local variable constants (e.g. sql = "UPDATE...")
            Map<String, String> localConstants = new HashMap<>();

            for (Stmt stmt : graph.getStmts()) {
                // Check if assignment: left = right
                if (stmt instanceof sootup.core.jimple.common.stmt.JAssignStmt) {
                    sootup.core.jimple.common.stmt.JAssignStmt assign = (sootup.core.jimple.common.stmt.JAssignStmt) stmt;
                    sootup.core.jimple.basic.Value left = assign.getLeftOp();
                    sootup.core.jimple.basic.Value right = assign.getRightOp();

                    if (right instanceof StringConstant && left instanceof sootup.core.jimple.basic.Local) {
                        localConstants.put(((sootup.core.jimple.basic.Local) left).getName(),
                                ((StringConstant) right).getValue());
                    }
                }

                if (stmt.containsInvokeExpr()) {
                    AbstractInvokeExpr invokeExpr = stmt.getInvokeExpr();

                    if (isJdbcUpdate(invokeExpr)) {
                        String tableName = resolveTableName(invokeExpr, incomingArgs, paramLocals, localConstants);
                        if (tableName != null) {
                            // Approximate location
                            Location loc = Location.at(currentMethodSignature.getDeclClassType().getClassName(), 0, 0);
                            tableUpdates.computeIfAbsent(tableName, k -> new ArrayList<>())
                                    .add(new UpdateOperation(tableName, invokeExpr.getMethodSignature().getName(),
                                            loc));
                        }
                    }

                    // Recurse calls
                    MethodSignature target = invokeExpr.getMethodSignature();
                    if (context.view().getClass(target.getDeclClassType()).isPresent()) {
                        // Resolve args for the target call
                        List<String> nextArgs = new ArrayList<>();
                        for (sootup.core.jimple.basic.Value arg : invokeExpr.getArgs()) {
                            String resolved = resolveStringValue(arg, incomingArgs, paramLocals, localConstants);
                            nextArgs.add(resolved);
                        }
                        traverseAndCollectUpdates(target, context, recursionStack, tableUpdates, depth + 1, nextArgs);
                    }
                }
            }
        } finally {
            recursionStack.remove(currentMethodSignature);
        }
    }

    private String resolveStringValue(sootup.core.jimple.basic.Value val, List<String> incomingArgs,
            List<sootup.core.jimple.basic.Local> paramLocals, Map<String, String> localConstants) {
        if (val instanceof StringConstant) {
            return ((StringConstant) val).getValue();
        }

        // Check if val is a local variable with known constant value
        if (val instanceof sootup.core.jimple.basic.Local) {
            String localName = ((sootup.core.jimple.basic.Local) val).getName();
            if (localConstants != null && localConstants.containsKey(localName)) {
                return localConstants.get(localName);
            }
        }

        // Check if val is a parameter local
        int paramIndex = paramLocals.indexOf(val);
        if (paramIndex >= 0 && incomingArgs != null && paramIndex < incomingArgs.size()) {
            return incomingArgs.get(paramIndex);
        }
        return null;
    }

    private String resolveTableName(AbstractInvokeExpr invokeExpr, List<String> incomingArgs,
            List<sootup.core.jimple.basic.Local> paramLocals, Map<String, String> localConstants) {
        if (invokeExpr.getArgs().isEmpty())
            return "unknown";
        var arg0 = invokeExpr.getArg(0);

        String sql = resolveStringValue(arg0, incomingArgs, paramLocals, localConstants);

        if (sql != null) {
            Matcher matcher = TABLE_NAME_PATTERN.matcher(sql);
            if (matcher.find()) {
                return matcher.group(1);
            }
        }
        return null;
    }

    record UpdateOperation(String tableName, String operation, Location location) {
    }

    private boolean isTransactional(SootMethod method, JavaView view) {
        if (method instanceof JavaSootMethod javaMethod) {
            java.util.Optional<JavaView> optView = java.util.Optional.of(view);
            for (sootup.java.core.AnnotationUsage a : javaMethod.getAnnotations(optView)) {
                if (a.toString().contains("Transactional")) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean isJdbcUpdate(AbstractInvokeExpr invokeExpr) {
        String methodName = invokeExpr.getMethodSignature().getName();
        String className = invokeExpr.getMethodSignature().getDeclClassType().toString();
        return (methodName.startsWith("update") || methodName.startsWith("batchUpdate")
                || methodName.startsWith("execute"))
                && (className.contains("JdbcTemplate") || className.contains("EntityManager"));
    }

    private static class ViolationsHelper {
        static void addViolation(List<Violation> violations, RuleDefinition rule, SootMethod method,
                List<UpdateOperation> updates) {
            String msg = String.format("Transaction '%s' updates table '%s' %d times. Consolidate updates.",
                    method.getName(), updates.get(0).tableName(), updates.size());

            violations.add(new Violation(
                    rule.id(),
                    rule.id(),
                    rule.severity(),
                    msg,
                    Location.at(method.getDeclaringClassType().toString() + "." + method.getName(), 0, 0),
                    Collections.emptyList(),
                    Collections.emptyMap()));
        }
    }
}
