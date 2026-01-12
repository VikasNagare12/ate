package com.vidnyan.ate.adapter.out.evaluator;

import com.vidnyan.ate.domain.graph.CallEdge;
import com.vidnyan.ate.domain.graph.CallGraph;
import com.vidnyan.ate.domain.model.Location;
import com.vidnyan.ate.domain.model.MethodEntity;
import com.vidnyan.ate.domain.model.SourceModel;
import com.vidnyan.ate.domain.rule.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.*;

/**
 * Evaluates transaction boundary violations using Backward Analysis.
 * 
 * Strategy:
 * 1. Identify "Sink Callers" (methods that call Sinks like RestTemplate).
 * 2. Traverse UPSTREAM from these callers to find if they are invoked inside
 * a @Transactional context.
 * 3. Stop upstream traversal if an @Async boundary is crossed.
 */
@Slf4j
@Component
public class TransactionBoundaryEvaluatorV2 implements RuleEvaluator {
    
    private static final String TRANSACTIONAL = "Transactional";
    private static final String ASYNC = "Async";
    
    @Override
    public boolean supports(RuleDefinition rule) {
        return "TX-BOUNDARY-001".equals(rule.id());
    }
    
    @Override
    public EvaluationResult evaluate(EvaluationContext context) {
        Instant start = Instant.now();
        SourceModel model = context.sourceModel();
        CallGraph callGraph = context.callGraph();
        RuleDefinition rule = context.rule();
        
        log.debug("Evaluating rule {} with Backward Analysis Strategy (V2)", rule.id());
        
        List<Violation> violations = new ArrayList<>();
        int nodesAnalyzed = 0;
        
        // 1. Identify Sinks (from JSON configuration)
        Set<String> sinkTypes = new HashSet<>(rule.detection().sinks().types());

        // 2. Find all Application Methods that call these Sinks "Directly"
        // This is our starting set for backward traversal
        Set<String> sinkCallers = new HashSet<>();

        for (String methodFqn : callGraph.getApplicationMethods()) {
            nodesAnalyzed++;
            for (CallEdge edge : callGraph.getOutgoingCalls(methodFqn)) {
                String callee = edge.resolvedCalleeFqn() != null ? edge.resolvedCalleeFqn() : edge.calleeFqn();
                if (isSink(callee, sinkTypes)) {
                    sinkCallers.add(methodFqn);
                    break;
                }
            }
        }

        log.debug("Found {} methods directly calling sinks", sinkCallers.size());

        // 3. Traverse Backward from each Sink Caller
        for (String startMethod : sinkCallers) {
            List<List<String>> paths = findTransactionalPathsValues(startMethod, model, callGraph);

            for (List<String> path : paths) {
                // Path is: SinkCaller <- ... <- TransactionalMethod
                // We want to report it as: TransactionalMethod -> ... -> SinkCaller -> Sink
                Collections.reverse(path);
                String transactionalMethod = path.get(0);
                String sinkCaller = path.get(path.size() - 1); // The method that actually calls the sink

                // We need to identify WHICH sink was called for the message
                String specificSink = findSinkCalledBy(sinkCaller, sinkTypes, callGraph);
                String sinkType = extractType(specificSink);

                // Add the sink itself to the chain for completeness
                List<String> fullChain = new ArrayList<>(path);
                fullChain.add(specificSink);

                violations.add(Violation.builder()
                        .ruleId(rule.id())
                        .ruleName(rule.name())
                        .severity(rule.severity())
                        .message(String.format(
                                "@Transactional method '%s' indirectly calls %s (via %s) which violates rule: %s",
                                        getSimpleName(transactionalMethod),
                                sinkType,
                                getSimpleName(sinkCaller),
                                        rule.name()
                        ))
                        .location(model.getMethod(transactionalMethod).map(MethodEntity::location)
                                .orElse(Location.at("unknown", 0, 0)))
                        .callChain(fullChain)
                        .build());
            }
        }
        
        Duration duration = Duration.between(start, Instant.now());
        log.debug("Rule {} found {} violations in {}ms", rule.id(), violations.size(), duration.toMillis());

        return EvaluationResult.success(rule.id(), violations, duration, nodesAnalyzed);
    }
    
    /**
     * Finds paths UPSTREAM from startMethod to any @Transactional method.
     * Respects @Async boundaries (stops traversal).
     */
    private List<List<String>> findTransactionalPathsValues(
            String startMethod,
            SourceModel model,
                    CallGraph callGraph) {

        List<List<String>> paths = new ArrayList<>();
        Set<String> visited = new HashSet<>();
        Queue<PathNode> queue = new LinkedList<>();

        queue.add(new PathNode(startMethod, new ArrayList<>(List.of(startMethod))));
        visited.add(startMethod);

        while (!queue.isEmpty()) {
            PathNode current = queue.poll();
            String methodFqn = current.methodFqn;

            // 1. Check if current method is @Transactional
            if (hasAnnotation(methodFqn, TRANSACTIONAL, model)) {
                paths.add(current.chain);
                // We continue searching other paths?
                // Usually one path to a transaction is enough to report, but let's find all
                // distinct ones if possible
                continue;
            }

            // 2. Check if current method is @Async -> STOP traversal on this branch
            if (hasAnnotation(methodFqn, ASYNC, model)) {
                continue;
            }

            // 3. Move Upstream (Find callers)
            Set<String> parents = new HashSet<>();

            // a. Direct Callers
            for (String caller : callGraph.getCallers(methodFqn)) {
                if (callGraph.isApplicationMethod(caller)) {
                    parents.add(caller);
                }
            }

            // b. Interface Linking (Ad-hoc Backward Traversal)
            // If 'methodFqn' overrides an interface method, that interface method's callers
            // are also parents
            // This bridges the gap Impl <- Interface <- Caller
            List<String> interfaceMethods = findImplementedInterfaceMethods(methodFqn, model);
            parents.addAll(interfaceMethods);
            // Note: We don't need to look for callers of interface methods recursively
            // here,
            // because next iteration 'interfaceMethod' will be 'methodFqn' and we'll get
            // ITS callers (calls to the interface)

            for (String parent : parents) {
                if (!visited.contains(parent) && current.chain.size() < 30) { // Max depth
                    visited.add(parent);
                    List<String> newChain = new ArrayList<>(current.chain);
                    newChain.add(parent);
                    queue.add(new PathNode(parent, newChain));
                }
            }
        }

        return paths;
    }
    
    private boolean hasAnnotation(String methodFqn, String annotationPartial, SourceModel model) {
        return model.getMethod(methodFqn)
                .map(m -> m.hasAnnotation(annotationPartial)
                        || m.hasAnnotation("org.springframework.transaction.annotation." + annotationPartial)
                        || m.hasAnnotation("org.springframework.scheduling.annotation." + annotationPartial))
                .orElse(false);
    }

    // Helper to bridge Impl -> Interface
    private List<String> findImplementedInterfaceMethods(String methodFqn, SourceModel model) {
        List<String> results = new ArrayList<>();

        // 1. Get the class containing the method
        String typeFqn = extractTypeFqn(methodFqn);
        MethodEntity method = model.getMethod(methodFqn).orElse(null);
        if (method == null)
            return results;

        model.getType(typeFqn).ifPresent(type -> {
            // 2. iterate over all interfaces
            for (com.vidnyan.ate.domain.model.TypeRef ifaceRef : type.interfaces()) {
                String ifaceFqn = ifaceRef.fullyQualifiedName();
                model.getType(ifaceFqn).ifPresent(ifaceType -> {
                    // 3. Check if interface has a matching method
                    model.getMethodsInType(ifaceFqn).stream()
                            .filter(m -> m.simpleName().equals(method.simpleName())) // Simplified matching
                            // TODO: Match parameters for correctness
                            .findFirst()
                            .ifPresent(m -> results.add(m.fullyQualifiedName()));
                });
            }
        });
        
        return results;
    }

    private String getSimpleName(String fqn) {
        if (fqn.contains("#")) {
            return fqn.substring(fqn.indexOf("#") + 1);
        }
        return fqn;
    }

    private String extractTypeFqn(String methodFqn) {
        if (methodFqn.contains("#")) {
            return methodFqn.substring(0, methodFqn.indexOf("#"));
        }
        return "";
    }

    private boolean isSink(String methodFqn, Set<String> sinkTypes) {
        // Handle constructor calls "new RestTemplate()" -> "org...RestTemplate"
        // Handle method calls "org...RestTemplate#getForObject"
        for (String type : sinkTypes) {
            if (methodFqn.startsWith(type + "#") || methodFqn.startsWith(type + ".")) {
                return true;
            }
        }
        return false;
    }

    private String findSinkCalledBy(String methodFqn, Set<String> sinkTypes, CallGraph callGraph) {
        for (CallEdge edge : callGraph.getOutgoingCalls(methodFqn)) {
            String callee = edge.resolvedCalleeFqn() != null ? edge.resolvedCalleeFqn() : edge.calleeFqn();
            if (isSink(callee, sinkTypes)) {
                return callee;
            }
        }
        return "Unknown Sink";
    }

    private String extractType(String methodFqn) {
        if (methodFqn.contains("#")) {
            String fqn = methodFqn.substring(0, methodFqn.indexOf('#'));
            return fqn.substring(fqn.lastIndexOf('.') + 1);
        }
        if (methodFqn.contains(".")) {
            return methodFqn.substring(methodFqn.lastIndexOf('.') + 1);
        }
        return methodFqn;
    }

    private record PathNode(String methodFqn, List<String> chain) {
    }
}
