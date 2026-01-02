# Call Chain Tracking for Transaction Analysis

## Overview

The `CallGraph` now supports **call chain tracking** - the ability to trace execution paths like `a→b→c→d` through your codebase. This is essential for:

- **Transaction boundary analysis** - Understanding which methods execute within a transaction scope
- **Data flow tracing** - Following how data moves through your application
- **Performance analysis** - Identifying deep call stacks that might cause issues
- **Security auditing** - Tracing execution paths from entry points to sensitive operations

## Key Methods

### 1. `findCallChains(String startMethod, int maxDepth)`

Finds **all execution paths** starting from a method.

**Use Case:** Trace all possible execution flows from a controller endpoint.

```java
CallGraph callGraph = CallGraph.build(sourceModel);

// Find all paths from a REST controller method
List<List<String>> chains = callGraph.findCallChains(
    "com.example.UserController#getUser(Long)", 
    5  // max depth
);

// Print all execution paths
for (List<String> chain : chains) {
    System.out.println(CallGraph.formatCallChain(chain));
}
```

**Example Output:**
```
UserController.getUser(Long) → UserService.findById(Long) → UserRepository.findById(Long)
UserController.getUser(Long) → UserService.findById(Long) → CacheService.get(String)
UserController.getUser(Long) → AuditService.logAccess(String)
```

### 2. `findCallChainsToTarget(String startMethod, String targetMethod, int maxDepth)`

Finds **all paths from start to a specific target method**.

**Use Case:** Trace how a database operation is reached from an entry point.

```java
// Find all paths that lead to a database write
List<List<String>> paths = callGraph.findCallChainsToTarget(
    "com.example.OrderController#createOrder(OrderDTO)",
    "com.example.OrderRepository#save(Order)",
    10
);

if (paths.isEmpty()) {
    System.out.println("Database save is not reachable - potential bug!");
} else {
    System.out.println("Found " + paths.size() + " paths to database:");
    paths.forEach(path -> System.out.println("  " + CallGraph.formatCallChain(path)));
}
```

### 3. `findTransactionBoundaries(Set<String> transactionalMethods, int maxDepth)`

Traces **all call chains from @Transactional methods**.

**Use Case:** Understand transaction propagation and identify all methods executing within transaction scope.

```java
// Get all @Transactional methods
Set<String> txMethods = sourceModel.getMethods().values().stream()
    .filter(Method::isTransactional)
    .map(Method::getFullyQualifiedName)
    .collect(Collectors.toSet());

// Find all execution paths within transactions
Map<String, List<List<String>>> txBoundaries = 
    callGraph.findTransactionBoundaries(txMethods, 5);

// Analyze each transaction boundary
for (Map.Entry<String, List<List<String>>> entry : txBoundaries.entrySet()) {
    String txMethod = entry.getKey();
    List<List<String>> chains = entry.getValue();
    
    System.out.println("Transaction: " + txMethod);
    System.out.println("  Executes " + chains.size() + " different paths");
    
    // Check for long transactions
    int maxDepth = chains.stream().mapToInt(List::size).max().orElse(0);
    if (maxDepth > 5) {
        System.out.println("  ⚠️  WARNING: Deep call chain (" + maxDepth + " levels)");
    }
}
```

## Real-World Use Cases

### Use Case 1: Detect Transaction Boundary Violations

**Problem:** Non-transactional methods calling transactional methods can cause unexpected behavior.

```java
public void detectTransactionViolations(CallGraph callGraph, SourceModel model) {
    Set<String> txMethods = model.getMethods().values().stream()
        .filter(Method::isTransactional)
        .map(Method::getFullyQualifiedName)
        .collect(Collectors.toSet());
    
    Set<String> nonTxMethods = model.getMethods().values().stream()
        .filter(m -> !m.isTransactional())
        .map(Method::getFullyQualifiedName)
        .collect(Collectors.toSet());
    
    for (String nonTxMethod : nonTxMethods) {
        for (String txMethod : txMethods) {
            List<List<String>> paths = callGraph.findCallChainsToTarget(
                nonTxMethod, txMethod, 3
            );
            
            if (!paths.isEmpty()) {
                System.out.println("⚠️  Violation: " + nonTxMethod + 
                                 " calls @Transactional " + txMethod);
                System.out.println("   Path: " + CallGraph.formatCallChain(paths.get(0)));
            }
        }
    }
}
```

### Use Case 2: Trace Data Flow to Database

**Problem:** Understanding how user input flows to database operations.

```java
public void traceDataFlowToDatabase(CallGraph callGraph) {
    String entryPoint = "com.example.api.UserController#createUser(UserDTO)";
    String dbOperation = "com.example.repository.UserRepository#save(User)";
    
    List<List<String>> paths = callGraph.findCallChainsToTarget(
        entryPoint, dbOperation, 10
    );
    
    System.out.println("Data flow from API to Database:");
    for (int i = 0; i < paths.size(); i++) {
        System.out.println((i+1) + ". " + CallGraph.formatCallChain(paths.get(i)));
        
        // Identify validation/transformation steps
        List<String> path = paths.get(i);
        boolean hasValidation = path.stream()
            .anyMatch(m -> m.contains("validate") || m.contains("Validator"));
        boolean hasMapping = path.stream()
            .anyMatch(m -> m.contains("map") || m.contains("Mapper"));
            
        if (!hasValidation) {
            System.out.println("   ⚠️  No validation detected in this path!");
        }
        if (!hasMapping) {
            System.out.println("   ⚠️  No DTO mapping detected!");
        }
    }
}
```

### Use Case 3: Identify Long Transaction Scopes

**Problem:** Long-running transactions can cause performance issues and deadlocks.

```java
public void identifyLongTransactions(CallGraph callGraph, SourceModel model) {
    Set<String> txMethods = model.getMethods().values().stream()
        .filter(Method::isTransactional)
        .map(Method::getFullyQualifiedName)
        .collect(Collectors.toSet());
    
    Map<String, List<List<String>>> txBoundaries = 
        callGraph.findTransactionBoundaries(txMethods, 10);
    
    for (Map.Entry<String, List<List<String>>> entry : txBoundaries.entrySet()) {
        String txMethod = entry.getKey();
        List<List<String>> chains = entry.getValue();
        
        // Find the longest chain
        int maxDepth = chains.stream()
            .mapToInt(List::size)
            .max()
            .orElse(0);
        
        if (maxDepth > 5) {
            System.out.println("⚠️  Long transaction detected:");
            System.out.println("   Method: " + txMethod);
            System.out.println("   Max depth: " + maxDepth + " method calls");
            
            // Show the longest path
            List<String> longestPath = chains.stream()
                .max((c1, c2) -> Integer.compare(c1.size(), c2.size()))
                .orElse(List.of());
            System.out.println("   Path: " + CallGraph.formatCallChain(longestPath));
            
            System.out.println("   💡 Consider: Breaking into smaller transactions");
        }
    }
}
```

### Use Case 4: Audit Security-Sensitive Operations

**Problem:** Ensure sensitive operations are only called through authorized paths.

```java
public void auditSecurityPaths(CallGraph callGraph) {
    String sensitiveOperation = "com.example.service.PaymentService#processPayment(Payment)";
    
    // Find all methods that can reach the sensitive operation
    List<String> callers = callGraph.getCallers(sensitiveOperation);
    
    System.out.println("Methods that can trigger payment processing:");
    for (String caller : callers) {
        // Trace back to entry points
        List<List<String>> pathsFromCaller = callGraph.findCallChains(caller, 5);
        
        // Check if path goes through security checks
        boolean hasSecurityCheck = pathsFromCaller.stream()
            .flatMap(List::stream)
            .anyMatch(m -> m.contains("Security") || 
                          m.contains("authorize") || 
                          m.contains("authenticate"));
        
        if (!hasSecurityCheck) {
            System.out.println("⚠️  SECURITY RISK: " + caller);
            System.out.println("   No security check detected in call path!");
        }
    }
}
```

## Performance Considerations

### Depth Limits

Call chain analysis uses depth-first search with backtracking. For large codebases:

- **Recommended max depth:** 5-10 levels
- **For transaction analysis:** 5 levels is usually sufficient
- **For full reachability:** 10-15 levels max

```java
// Good: Focused analysis
List<List<String>> chains = callGraph.findCallChains(method, 5);

// Risky: May be slow on large codebases
List<List<String>> chains = callGraph.findCallChains(method, 20);
```

### Cycle Detection

The implementation automatically detects and avoids cycles:

```java
// This won't cause infinite loops even if a→b→c→a exists
List<List<String>> chains = callGraph.findCallChains("a", 10);
```

### Memory Usage

Each call chain is stored as a separate list. For methods with many execution paths:

```java
// Check number of paths before processing
List<List<String>> chains = callGraph.findCallChains(method, 5);
if (chains.size() > 1000) {
    System.out.println("Warning: " + chains.size() + " paths found!");
    // Consider reducing max depth or filtering
}
```

## Integration with Rules

You can use call chain tracking in custom rules:

```java
// Example rule: Detect @Scheduled methods calling @Transactional methods
public class ScheduledTransactionRule implements Rule {
    @Override
    public List<Violation> evaluate(RuleContext context) {
        List<Violation> violations = new ArrayList<>();
        
        Set<String> scheduledMethods = context.getSourceModel()
            .getMethods().values().stream()
            .filter(Method::isScheduled)
            .map(Method::getFullyQualifiedName)
            .collect(Collectors.toSet());
        
        Set<String> txMethods = context.getSourceModel()
            .getMethods().values().stream()
            .filter(Method::isTransactional)
            .map(Method::getFullyQualifiedName)
            .collect(Collectors.toSet());
        
        for (String scheduledMethod : scheduledMethods) {
            for (String txMethod : txMethods) {
                List<List<String>> paths = context.getCallGraph()
                    .findCallChainsToTarget(scheduledMethod, txMethod, 5);
                
                if (!paths.isEmpty()) {
                    violations.add(new Violation(
                        "SCHEDULED_CALLS_TRANSACTIONAL",
                        "Scheduled job calls @Transactional method",
                        Severity.WARN,
                        CallGraph.formatCallChain(paths.get(0))
                    ));
                }
            }
        }
        
        return violations;
    }
}
```

## Summary

The call chain tracking feature enables powerful analysis capabilities:

✅ **Transaction boundary analysis** - Understand transaction scope and propagation  
✅ **Data flow tracing** - Follow data from entry points to persistence  
✅ **Performance analysis** - Identify deep call stacks  
✅ **Security auditing** - Verify authorization paths  
✅ **Architectural validation** - Ensure layering rules are followed  

With 25 years of architecture experience, you'll find these tools invaluable for maintaining clean, performant, and secure enterprise applications!
