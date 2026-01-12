package com.vidnyan.ate.adapter.out.evaluator;

import com.vidnyan.ate.domain.graph.CallEdge;
import com.vidnyan.ate.domain.graph.CallGraph;
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
            Map<String, List<String>> tableUpdates = new HashMap<>();

            // Traverse call graph to find JDBC updates
            // Traverse call graph to find JDBC updates
            traverseAndCollectUpdates(rootFqn, callGraph, model, new HashSet<>(), tableUpdates, 0, List.of());

            // Check for duplicates
            tableUpdates.forEach((tableName, locations) -> {
                log.debug("Table '{}' updated at locations: {}", tableName, locations);
                if (locations.size() > 1) {
                    model.getMethod(rootFqn).ifPresent(rootMethod -> {
                        violations.add(Violation.builder()
                                .ruleId(rule.id())
                                .ruleName(rule.name())
                                .severity(rule.severity())
                                .message(String.format(
                                        "Multiple updates to table '%s' detected within transaction '%s'. Locations: %s",
                                        tableName, rootMethod.simpleName(), locations))
                                .location(rootMethod.location())
                                .callChain(List.of(rootFqn)) // Simplified chain for now
                                .build());
                    });
                }
            });
        }

        Duration duration = Duration.between(start, Instant.now());
        return EvaluationResult.success(rule.id(), violations, duration, pathsAnalyzed);
    }

    private void traverseAndCollectUpdates(String currentMethodFqn, CallGraph callGraph, SourceModel model,
            Set<String> currentPath, Map<String, List<String>> tableUpdates, int depth,
            List<String> incomingArguments) {

        if (depth > 50)
            return; // Prevent stack overflow on deep graphs
        if (currentPath.contains(currentMethodFqn))
            return; // Cycle detection

        currentPath.add(currentMethodFqn);

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
                        tableUpdates.computeIfAbsent(tableName, k -> new ArrayList<>())
                                .add(edge.location().toString());
                    }
                } else if (callGraph.isApplicationMethod(callee)) {
                    // Continue traversal with resolved arguments
                    traverseAndCollectUpdates(callee, callGraph, model, currentPath, tableUpdates, depth + 1,
                            resolvedArguments);
                }
            }
        } finally {
            currentPath.remove(currentMethodFqn); // Allow revisiting from other paths
        }
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
