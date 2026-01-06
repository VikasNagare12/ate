# Call Graph Integration in ATE Rule Engine

## Overview

The **CallGraph is fully integrated** into the ATE rule engine and ready to use for all boundary analysis scenarios:

✅ **Transaction Boundary Analysis** - Detect improper transaction propagation  
✅ **Async Boundary Analysis** - Find async/transaction conflicts  
✅ **Retry Boundary Analysis** - Identify non-idempotent operations in retry scopes  
✅ **Custom Boundary Analysis** - Create your own rules using call chain tracking  

## Architecture

```
AnalysisEngine
    ↓
    ├─ Builds CallGraph from SourceModel
    ↓
RuleEngine.initialize(sourceModel, callGraph, dependencyGraph)
    ↓
RuleEngine.detectViolationsRules()
    ↓
RuleEvaluator.detectViolations(rule, sourceModel, callGraph, dependencyGraph)
    ↓
    ├─ GraphTraversalEvaluator (uses callGraph.findReachableMethods())
    ├─ CustomEvaluator (can use callGraph.findCallChains())
    └─ YourEvaluator (full access to CallGraph API)
```

**Key Point:** Every rule evaluator receives the `CallGraph` instance, so you can use all call chain tracking methods.

## Available CallGraph Methods

### 1. Basic Queries

```java
// Get direct callees
List<String> callees = callGraph.getCallees(methodSignature);

// Get direct callers
List<String> callers = callGraph.getCallers(methodSignature);

// Get all call sites (with location info)
List<Relationship> sites = callGraph.getCallSites(methodSignature);
```

### 2. Reachability Analysis

```java
// Find all reachable methods (transitive closure)
Set<String> reachable = callGraph.findReachableMethods(startMethod, maxDepth);
```

### 3. Call Chain Tracking (NEW!)

```java
// Find ALL execution paths from a method
List<List<String>> chains = callGraph.findCallChains(startMethod, maxDepth);
// Returns: [[a, b, c], [a, b, d], [a, e]]

// Find paths to a specific target
List<List<String>> paths = callGraph.findCallChainsToTarget(start, target, maxDepth);

// Analyze transaction boundaries
Map<String, List<List<String>>> txBoundaries = 
    callGraph.findTransactionBoundaries(transactionalMethods, maxDepth);

// Format chains for display
String formatted = CallGraph.formatCallChain(chain);
// Output: "Controller.create() → Service.save() → Repository.insert()"
```

## Example Rules (Ready to Use!)

I've created 4 production-ready rules that demonstrate call graph usage:

### 1. Async-Transaction Boundary Detection

**File:** `async-transaction-boundary.json`

**Detects:** `@Async` methods calling `@Transactional` methods

**Why it matters:** Transaction context is not propagated to async threads, causing `LazyInitializationException` and data inconsistency.

```json
{
  "ruleId": "ASYNC_TRANSACTION_BOUNDARY",
  "query": {
    "type": "graph_traversal",
    "graph": "call_graph",
    "pattern": {
      "start": { "annotation": "Async" },
      "traverse": { "maxDepth": 5 },
      "target": {
        "mustNotReach": [{ "annotation": "Transactional" }]
      }
    }
  }
}
```

### 2. Transactional-Calls-Async Detection

**File:** `transactional-calls-async.json`

**Detects:** `@Transactional` methods calling `@Async` methods

**Why it matters:** Async execution happens outside transaction boundary, risking data inconsistency if async fails after commit.

### 3. Retry Without Idempotency

**File:** `retry-without-idempotency.json`

**Detects:** `@Retryable` methods calling non-idempotent operations (emails, payments, notifications)

**Why it matters:** Retries can cause duplicate charges, duplicate emails, etc.

```json
{
  "target": {
    "mustNotReach": [
      { "pattern": ".*EmailService.*send.*" },
      { "pattern": ".*PaymentService.*charge.*" },
      { "pattern": ".*NotificationService.*notify.*" }
    ]
  }
}
```

### 4. Nested Transaction Propagation

**File:** `nested-transaction-propagation.json`

**Detects:** `@Transactional` methods calling other `@Transactional` methods

**Why it matters:** Default REQUIRED propagation means both share same transaction, which may not be intended.

## How to Use in Application.properties

Add the new rules to your configuration:

```properties
ate.analysis.rule-files[0]=src/main/resources/rules/scheduled-job-resiliency.json
ate.analysis.rule-files[1]=src/main/resources/rules/transaction-boundary-violation.json
ate.analysis.rule-files[2]=src/main/resources/rules/circular-dependency.json
ate.analysis.rule-files[3]=src/main/resources/rules/layered-architecture.json
# NEW RULES
ate.analysis.rule-files[4]=src/main/resources/rules/async-transaction-boundary.json
ate.analysis.rule-files[5]=src/main/resources/rules/transactional-calls-async.json
ate.analysis.rule-files[6]=src/main/resources/rules/retry-without-idempotency.json
ate.analysis.rule-files[7]=src/main/resources/rules/nested-transaction-propagation.json
```

## Creating Custom Rules

### Example: Detect Controllers Calling Repositories Directly

Controllers should call Services, not Repositories directly (layering violation).

```json
{
  "ruleId": "CONTROLLER_BYPASSES_SERVICE",
  "name": "Controllers Must Not Call Repositories Directly",
  "severity": "ERROR",
  "query": {
    "type": "graph_traversal",
    "graph": "call_graph",
    "pattern": {
      "start": {
        "annotation": "RestController"
      },
      "traverse": {
        "maxDepth": 2
      },
      "target": {
        "mustNotReach": [
          {
            "annotation": "Repository"
          }
        ]
      }
    }
  },
  "violation": {
    "message": "Controller '{method}' calls Repository '{target}' directly. Use Service layer.",
    "remediation": "Create or use an existing Service class to encapsulate business logic."
  }
}
```

### Example: Detect Scheduled Jobs Without Retry

```json
{
  "ruleId": "SCHEDULED_WITHOUT_RETRY",
  "name": "Scheduled Jobs Should Use Retry",
  "severity": "WARN",
  "query": {
    "type": "graph_traversal",
    "graph": "call_graph",
    "pattern": {
      "start": {
        "annotation": "Scheduled"
      },
      "traverse": {
        "maxDepth": 5
      },
      "target": {
        "mustReach": [
          {
            "annotation": "Retryable"
          }
        ]
      }
    }
  }
}
```

## Creating Custom Evaluators

If you need more complex logic than JSON rules support, create a custom evaluator:

```java
@Component
public class TransactionChainAnalyzer implements RuleEvaluator {
    
    @Override
    public boolean isApplicable(RuleDefinition rule) {
        return "transaction_chain_analysis".equals(rule.getQuery().getType());
    }
    
    @Override
    public List<Violation> detectViolations(RuleDefinition rule, SourceModel sourceModel, 
                                    CallGraph callGraph, DependencyGraph dependencyGraph) {
        List<Violation> violations = new ArrayList<>();
        
        // Get all @Transactional methods
        Set<String> txMethods = sourceModel.getMethods().values().stream()
            .filter(Method::isTransactional)
            .map(Method::getFullyQualifiedName)
            .collect(Collectors.toSet());
        
        // Analyze transaction boundaries
        Map<String, List<List<String>>> txBoundaries = 
            callGraph.findTransactionBoundaries(txMethods, 5);
        
        for (Map.Entry<String, List<List<String>>> entry : txBoundaries.entrySet()) {
            String txMethod = entry.getKey();
            List<List<String>> chains = entry.getValue();
            
            // Find longest chain
            int maxDepth = chains.stream()
                .mapToInt(List::size)
                .max()
                .orElse(0);
            
            // Flag deep transaction chains
            if (maxDepth > 5) {
                violations.add(Violation.builder()
                    .ruleId(rule.getRuleId())
                    .severity(Severity.WARN)
                    .message("Transaction " + txMethod + " has deep call chain (" + maxDepth + " levels)")
                    .location(sourceModel.getMethod(txMethod).getLocation())
                    .build());
            }
        }
        
        return violations;
    }
}
```

## Performance Considerations

### Depth Limits

- **Transaction analysis:** maxDepth = 3-5 (transactions should be short)
- **Async boundary:** maxDepth = 5 (async calls are usually near entry points)
- **Retry analysis:** maxDepth = 5 (retry scopes should be focused)
- **General reachability:** maxDepth = 10 (for deep analysis)

### Rule Optimization

```json
{
  "traverse": {
    "maxDepth": 3  // Lower depth = faster evaluation
  }
}
```

## Real-World Benefits (25 Years Architecture Experience!)

### 1. Transaction Management

- ✅ Detect transaction boundary violations automatically
- ✅ Prevent `LazyInitializationException` before production
- ✅ Ensure proper transaction propagation
- ✅ Identify long-running transactions

### 2. Async Processing

- ✅ Catch async/transaction conflicts early
- ✅ Verify async boundaries are properly placed
- ✅ Ensure data consistency in async flows

### 3. Resilience Patterns

- ✅ Validate retry logic doesn't cause duplicate operations
- ✅ Ensure idempotency in retry scopes
- ✅ Detect circuit breaker misuse

### 4. Architectural Governance

- ✅ Enforce layering (Controller → Service → Repository)
- ✅ Prevent architectural drift
- ✅ Maintain clean boundaries

## Summary

The CallGraph is **production-ready** and integrated into the rule engine:

✅ **Available in all evaluators** via `RuleEvaluator.detectViolations()` method  
✅ **4 example rules** for common boundary violations  
✅ **Full API** for custom analysis (findCallChains, findTransactionBoundaries, etc.)  
✅ **Performance optimized** with depth limits and cycle detection  
✅ **Extensible** - create custom evaluators for complex scenarios  

The call graph enables **proactive detection** of boundary violations that typically only surface in production. With 25 years of architecture experience, you know how valuable this is for maintaining enterprise application quality!
