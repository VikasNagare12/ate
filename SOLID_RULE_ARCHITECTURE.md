# SOLID Rule Architecture

## Problem with Old Approach

**Mixed Concerns:**
```
JSON (policy + implementation details)
  ↓
GraphTraversalEvaluator (one class handles everything)
  ↓
Complex if/else logic for different rule types
```

**Issues:**
- ❌ JSON contains implementation details (maxDepth, graph traversal logic)
- ❌ One evaluator class handles all rule types (violates SRP)
- ❌ Hard to add new rule types (violates OCP)
- ❌ Policy and implementation are coupled

## New SOLID Architecture

### Separation of Concerns

```
JSON (WHAT - Policy)
  ↓
ArchitecturalRule (Model)
  ↓
RuleChecker (Interface)
  ↓
Specific Checker Classes (HOW - Implementation)
```

### SOLID Principles Applied

#### 1. Single Responsibility Principle (SRP)
Each checker class has ONE responsibility:

```java
AnnotatedMethodInvocationChecker
  → Checks method invocations from annotated methods

PackageDependencyChecker
  → Checks package dependencies

TransactionDepthChecker
  → Checks transaction call chain depth
```

#### 2. Open/Closed Principle (OCP)
Add new rule types without modifying existing code:

```java
// Add new checker - no changes to existing checkers
@Component
public class SecurityBoundaryChecker implements RuleChecker {
    // New functionality
}
```

#### 3. Liskov Substitution Principle (LSP)
All checkers are interchangeable via the interface:

```java
List<RuleChecker> checkers = [...];  // Any RuleChecker works
for (RuleChecker checker : checkers) {
    if (checker.isApplicable(rule)) {
        violations.addAll(checker.check(rule, ...));
    }
}
```

#### 4. Interface Segregation Principle (ISP)
Clean, focused interface:

```java
public interface RuleChecker {
    boolean isApplicable(ArchitecturalRule rule);
    List<Violation> check(...);
}
```

#### 5. Dependency Inversion Principle (DIP)
Depend on abstractions, not concretions:

```java
// Engine depends on interface, not specific checkers
private final List<RuleChecker> checkers;  // Interface

// Spring auto-discovers all implementations
@Component
public class AnnotatedMethodInvocationChecker implements RuleChecker { ... }
```

## JSON Format (Policy Only)

### Example: Async-Transaction Boundary

```json
{
  "id": "ASYNC-TX-BOUNDARY-001",
  "name": "Async Methods Must Not Call Transactional Methods",
  "enabled": true,
  "severity": "ERROR",
  "confidence": "HIGH",

  "description": "Methods annotated with @Async must not invoke methods annotated with @Transactional",

  "target": {
    "type": "ANNOTATED_METHOD",
    "annotation": "Async"
  },

  "constraints": {
    "mustNotInvokeAnnotatedMethods": ["Transactional"],
    "maxCallDepth": 5
  },

  "rationale": {
    "why": "Transaction context is not propagated to async threads",
    "impact": "LazyInitializationException, partial commits, data inconsistency"
  },

  "remediation": {
    "summary": "Move async boundary outside transaction or manage transactions explicitly",
    "options": [
      "Remove @Transactional from the called method",
      "Move @Async after transaction completes",
      "Use TransactionTemplate in async execution"
    ]
  },

  "tags": ["transaction", "async", "spring", "concurrency"]
}
```

### What JSON Contains (WHAT)
✅ **id** - Unique identifier  
✅ **target** - Where to apply (which methods)  
✅ **constraints** - What is forbidden/required  
✅ **rationale** - Why this rule exists  
✅ **remediation** - How to fix violations  

### What JSON Does NOT Contain (HOW)
❌ Graph traversal algorithms  
❌ AST parsing logic  
❌ Detection implementation  
❌ Engine internals  

## Java Implementation (HOW)

### RuleChecker Interface

```java
public interface RuleChecker {
    boolean isApplicable(ArchitecturalRule rule);
    List<Violation> check(ArchitecturalRule rule, 
                          SourceModel sourceModel, 
                          CallGraph callGraph, 
                          DependencyGraph dependencyGraph);
}
```

### Example Checker

```java
@Component
public class AnnotatedMethodInvocationChecker implements RuleChecker {
    
    @Override
    public boolean isApplicable(ArchitecturalRule rule) {
        return rule.getTarget().getType() == ANNOTATED_METHOD
            && rule.getConstraints().getMustNotInvokeAnnotatedMethods() != null;
    }
    
    @Override
    public List<Violation> check(ArchitecturalRule rule, ...) {
        // 1. Find target methods (WHAT from JSON)
        List<Method> targets = sourceModel.getMethodsAnnotatedWith(
            rule.getTarget().getAnnotation()
        );
        
        // 2. Check constraints (HOW - implementation)
        for (Method method : targets) {
            Set<String> reachable = callGraph.findReachableMethods(...);
            // Check if forbidden methods are reachable
            // Create violations if found
        }
        
        return violations;
    }
}
```

## Benefits

### 1. Stability
**JSON rules are stable** - no changes needed when engine improves:

```json
// This JSON never changes
{
  "target": { "annotation": "Async" },
  "constraints": { "mustNotInvokeAnnotatedMethods": ["Transactional"] }
}
```

```java
// Engine can improve detection without changing JSON
public List<Violation> check(...) {
    // V1: Simple reachability
    // V2: Add call chain tracking
    // V3: Add library boundary detection
    // V4: Add performance optimizations
    // JSON stays the same!
}
```

### 2. Readability
**Non-technical stakeholders can read JSON:**

```json
{
  "name": "Async Methods Must Not Call Transactional Methods",
  "rationale": {
    "why": "Transaction context is not propagated to async threads",
    "impact": "LazyInitializationException, data inconsistency"
  }
}
```

### 3. Extensibility
**Add new rule types easily:**

```java
// New checker for security rules
@Component
public class SecurityBoundaryChecker implements RuleChecker {
    @Override
    public boolean isApplicable(ArchitecturalRule rule) {
        return rule.getConstraints().getMustNotReachExternalApis() != null;
    }
    
    @Override
    public List<Violation> check(...) {
        // Implementation for security boundary checks
    }
}
```

### 4. Testability
**Each checker is independently testable:**

```java
@Test
void testAsyncTransactionBoundary() {
    ArchitecturalRule rule = loadRule("async-tx-boundary.json");
    AnnotatedMethodInvocationChecker checker = new AnnotatedMethodInvocationChecker();
    
    List<Violation> violations = checker.check(rule, mockModel, mockGraph, null);
    
    assertEquals(1, violations.size());
    assertTrue(violations.get(0).getMessage().contains("@Async"));
}
```

## Migration Path

### Phase 1: Dual Support (Current)
- Keep old `RuleDefinition` + `GraphTraversalEvaluator`
- Add new `ArchitecturalRule` + `RuleChecker` implementations
- Both systems work side-by-side

### Phase 2: Migrate Rules
- Convert existing JSON rules to new format
- Test both old and new produce same violations
- Update documentation

### Phase 3: Deprecate Old System
- Mark old classes as `@Deprecated`
- Remove old evaluator
- Clean up codebase

## Example Rule Checkers to Implement

### 1. AnnotatedMethodInvocationChecker ✅
**Handles:** Method invocation constraints from annotated methods

**Examples:**
- @Async must not call @Transactional
- @Scheduled must call @Retryable
- @RestController must not call @Repository

### 2. PackageDependencyChecker
**Handles:** Package dependency constraints

**Examples:**
- Controller package must not depend on Repository package
- Domain package must not depend on Infrastructure package

### 3. TransactionDepthChecker
**Handles:** Transaction call chain depth

**Examples:**
- @Transactional methods must not have call chains deeper than 5
- Nested @Transactional calls must use REQUIRES_NEW

### 4. MethodPatternChecker
**Handles:** Method name/signature patterns

**Examples:**
- Methods matching `.*Service.*` must not call `.*Controller.*`
- Methods matching `.*Repository.*` must be in `@Repository` classes

## Summary

**Old Way:**
```
JSON (mixed policy + implementation)
  → One big evaluator class
  → Hard to extend
```

**New Way (SOLID):**
```
JSON (pure policy)
  → ArchitecturalRule (model)
  → RuleChecker (interface)
  → Specific checker classes (one per rule type)
  → Easy to extend, test, maintain
```

**Benefits:**
✅ Separation of concerns (WHAT vs HOW)  
✅ Single Responsibility (one checker per rule type)  
✅ Open/Closed (add checkers without modifying existing)  
✅ Testable (each checker independently testable)  
✅ Readable (JSON is pure policy)  
✅ Stable (JSON doesn't change when engine improves)  

This architecture is **production-ready** for enterprise systems with 25 years of architecture experience!
