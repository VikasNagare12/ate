package com.vidnyan.ate.adapter.out.evaluator;

import com.vidnyan.ate.domain.graph.CallEdge;
import com.vidnyan.ate.domain.graph.CallGraph;
import com.vidnyan.ate.domain.model.Location;
import com.vidnyan.ate.domain.model.SourceModel;
import com.vidnyan.ate.domain.rule.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Detects multiple update operations on the same table within a single
 * transaction.
 * This can indicate inefficient design or potential race conditions.
 */
@Slf4j
@Component
public class SameTableUpdateEvaluator implements RuleEvaluator {

    private static final Pattern TABLE_NAME_PATTERN = Pattern.compile("(?i)(?:update|insert\\s+into)\\s+(\\w+)");

    private static final Set<String> JDBC_TYPES = Set.of(
            "org.springframework.jdbc.core.JdbcTemplate",
            "org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate");

    @Override
    public boolean supports(RuleDefinition rule) {
        return "SAME-TABLE-UPDATE-001".equals(rule.id());
    }

    @Override
    public EvaluationResult evaluate(EvaluationContext context) {
        Instant start = Instant.now();
        SourceModel model = context.sourceModel();
        CallGraph callGraph = context.callGraph();
        RuleDefinition rule = context.rule();

        log.debug("Evaluating Same Table Update rule...");

        List<Violation> violations = new ArrayList<>();
        int pathsAnalyzed = 0;

        // 1. Find all @Transactional methods (roots for this rule)
        List<String> transactionalMethods = model.methods().values().stream()
                .filter(m -> hasTransactionalAnnotation(m.fullyQualifiedName(), model))
                .map(com.vidnyan.ate.domain.model.MethodEntity::fullyQualifiedName)
                .collect(Collectors.toList());

        for (String rootFqn : transactionalMethods) {
            pathsAnalyzed++;
            Map<String, List<UpdateOperation>> tableUpdates = new HashMap<>();

            // Traverse call graph to find JDBC updates
            // Traverse call graph to find JDBC updates
            // Traverse call graph to find JDBC updates
            traverseAndCollectUpdates(rootFqn, callGraph, model, new HashSet<>(), tableUpdates, 0, List.of(),
                    new ArrayList<>());

            // Check for duplicates
            tableUpdates.forEach((tableName, operations) -> {
                log.debug("Table '{}' updated at locations: {}", tableName, operations.size());
                if (operations.size() > 1) {
                    model.getMethod(rootFqn).ifPresent(rootMethod -> {
                        StringBuilder message = new StringBuilder();
                        message.append(String.format("Multiple updates to table '%s' detected within transaction '%s':",
                                tableName, rootMethod.simpleName()));

                        for (int i = 0; i < operations.size(); i++) {
                            UpdateOperation op = operations.get(i);
                            message.append(String.format("\n  %d. %s (Line %d)",
                                    i + 1,
                                    String.join(" -> ", op.callChain),
                                    op.location.line()));
                        }

                        violations.add(Violation.builder()
                                .ruleId(rule.id())
                                .ruleName(rule.name())
                                .severity(rule.severity())
                                .message(message.toString())
                                .location(rootMethod.location())
                                .callChain(List.of(rootFqn))
                                .build());
                    });
                }
            });
        }

        Duration duration = Duration.between(start, Instant.now());
        return EvaluationResult.success(rule.id(), violations, duration, pathsAnalyzed);
    }

    private void traverseAndCollectUpdates(String currentMethodFqn, CallGraph callGraph, SourceModel model,
            Set<String> currentPath, Map<String, List<UpdateOperation>> tableUpdates, int depth,
            List<String> incomingArguments, List<String> callChain) {

        if (depth > 50)
            return; // Prevent stack overflow on deep graphs
        if (currentPath.contains(currentMethodFqn))
            return; // Cycle detection

        currentPath.add(currentMethodFqn);

        // Add current method to call chain context for this path
        List<String> currentChain = new ArrayList<>(callChain);
        currentChain.add(getSimpleMethodName(currentMethodFqn));

        try {
            // Get current method parameters to map incoming arguments
            List<String> paramNames = model.getMethod(currentMethodFqn)
                    .map(m -> m.parameters().stream().map(p -> p.name()).collect(Collectors.toList()))
                    .orElse(List.of());

            // Check outgoing calls
            for (CallEdge edge : callGraph.getOutgoingCalls(currentMethodFqn)) {
                String callee = edge.effectiveCalleeFqn();
                if (callee == null)
                    continue;

                // Resolve arguments for this call
                List<String> resolvedArguments = new ArrayList<>();
                for (String arg : edge.arguments()) {
                    // Try to substitute if arg matches a parameter name
                    int paramIndex = paramNames.indexOf(arg);
                    if (paramIndex >= 0 && paramIndex < incomingArguments.size()) {
                        resolvedArguments.add(incomingArguments.get(paramIndex));
                    } else {
                        resolvedArguments.add(arg);
                    }
                }

                // Check if this is a JDBC update call
                if (isJdbcUpdate(callee)) {
                    String tableName = extractTableName(resolvedArguments);
                    if (tableName != null) {
                        List<String> fullChain = new ArrayList<>(currentChain);
                        fullChain.add(getSimpleMethodName(callee));

                        tableUpdates.computeIfAbsent(tableName, k -> new ArrayList<>())
                                .add(new UpdateOperation(edge.location(), fullChain));
                    }
                } else if (callGraph.isApplicationMethod(callee)) {
                    // Find target methods (exact or fuzzy match)
                            List<String> targets = findMethods(model, callee);

                    for (String targetFqn : targets) {
                        traverseAndCollectUpdates(targetFqn, callGraph, model, currentPath, tableUpdates, depth + 1,
                            resolvedArguments, currentChain);
                     }
                }
            }
        } finally {
            currentPath.remove(currentMethodFqn); // Allow revisiting from other paths
        }
    }

    private List<String> findMethods(SourceModel model, String calleeFqn) {
        // 1. Try exact match
        if (model.getMethod(calleeFqn).isPresent()) {
            return List.of(calleeFqn);
        }

        // 2. Fuzzy match (if callee has '?' or missing types)
        // Parse callee to get type and method name
        int hashIdx = calleeFqn.lastIndexOf('#');
        if (hashIdx == -1)
            return List.of();

        String typeName = calleeFqn.substring(0, hashIdx);
        String signature = calleeFqn.substring(hashIdx + 1);

        String methodName;
        int paramCount = 0;

        if (signature.contains("(")) {
            methodName = signature.substring(0, signature.indexOf('('));
            String params = signature.substring(signature.indexOf('(') + 1, signature.lastIndexOf(')'));
            if (!params.isEmpty()) {
                paramCount = params.split(",").length;
            }
        } else {
            methodName = signature;
        }

        // Search in model
        final String searchMethodName = methodName;
        final int searchParamCount = paramCount;

        return model.methods().values().stream()
                .filter(m -> m.containingTypeFqn().equals(typeName))
                .filter(m -> m.simpleName().equals(searchMethodName))
                .filter(m -> m.parameters().size() == searchParamCount)
                .map(com.vidnyan.ate.domain.model.MethodEntity::fullyQualifiedName)
                .collect(Collectors.toList());
    }

    private String getSimpleMethodName(String fqn) {
        int hashIdx = fqn.lastIndexOf('#');
        if (hashIdx > 0 && hashIdx < fqn.length() - 1) {
            String className = fqn.substring(0, hashIdx);
            String methodName = fqn.substring(hashIdx + 1);
            int lastDot = className.lastIndexOf('.');
            if (lastDot > 0) {
                className = className.substring(lastDot + 1);
            }
            if (methodName.contains("(")) {
                methodName = methodName.substring(0, methodName.indexOf('('));
            }
            return className + "." + methodName;
        }
        return fqn;
    }

    private record UpdateOperation(Location location, List<String> callChain) {
    }

    private boolean isJdbcUpdate(String calleeFqn) {
        boolean isJdbc = JDBC_TYPES.stream().anyMatch(calleeFqn::startsWith);
        if (!isJdbc)
            return false;

        String methodName = calleeFqn.substring(calleeFqn.lastIndexOf('#') + 1);
        if (methodName.contains("(")) {
            methodName = methodName.substring(0, methodName.indexOf('('));
        }

        return methodName.startsWith("update") ||
                methodName.startsWith("batchUpdate") ||
                methodName.startsWith("insert");
    }

    private String extractTableName(List<String> arguments) {
        if (arguments.isEmpty())
            return null;

        // Usually the SQL is the first argument
        String sql = arguments.get(0);
        // Remove quotes if present (it's a string literal in AST)
        if (sql.startsWith("\"") && sql.endsWith("\"")) {
            sql = sql.substring(1, sql.length() - 1);
        }

        Matcher matcher = TABLE_NAME_PATTERN.matcher(sql);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }

    private boolean hasTransactionalAnnotation(String methodFqn, SourceModel model) {
        return model.getMethod(methodFqn).map(method -> {
            if (method.hasAnnotation("Transactional"))
                return true;
            return model.getType(method.containingTypeFqn())
                    .map(t -> t.hasAnnotation("Transactional"))
                    .orElse(false);
        }).orElse(false);
    }
}
