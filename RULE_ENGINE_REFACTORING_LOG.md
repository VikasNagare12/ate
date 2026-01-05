# Refactoring Changes Log

## Summary
Refactored the Rule Engine to adhere to the "One Rule, One Class" architectural pattern. This improves modularity, type safety, and maintainability.

## File Changes

### Core Engine
- **Modified** `com.vidnyan.ate.rule.RuleDefinition`
  - Upgraded to a rich domain model with `Target`, `Constraints`, `Rationale`, etc.
  - Added support for v2 JSON schema.
- **Modified** `com.vidnyan.ate.rule.RuleEngine`
  - Updated to load `RuleDefinition` directly.
  - Logic updated to find supporting `RuleEvaluator` for each rule instance.
- **Deleted** `com.vidnyan.ate.rule.ArchitecturalRule`
  - Removed redundant interface.
- **Deprecated** `com.vidnyan.ate.rule.evaluator.GraphTraversalEvaluator`
  - Marked as `@Deprecated`.
  - Stubbed methods to be non-functional (returns empty list) to prevent misuse.

### Rule Evaluators (New)
Created specific evaluator classes for each architectural rule:
1. `AsyncTransactionBoundaryEvaluator` (`ASYNC-TX-BOUNDARY-001`)
2. `LayeredArchitectureEvaluator` (`LAYERED-ARCHITECTURE-001`)
3. `CircularDependencyEvaluator` (`CIRCULAR-DEPENDENCY-001`)
4. `TransactionalCallsAsyncEvaluator` (`TRANSACTIONAL-CALLS-ASYNC`)
5. `IdempotencyEvaluator` (`RETRY-IDEMPOTENCY-001`)
6. `ScheduledJobResiliencyEvaluator` (`SCHEDULED-RESILIENCY-001`)
7. `TransactionBoundaryEvaluator` (`TX-BOUNDARY-001`)

### Configuration (JSON Rules)
Updated all rule definitions in `src/main/resources/rules/` to the new standard schema:
- `async-tx-boundary-v2.json` (removed legacy version if exists, ensured `async-transaction-boundary.json` is aligned or essentially replaced)
- `layered-architecture.json`
- `circular-dependency.json`
- `transactional-calls-async.json`
- `retry-without-idempotency.json`
- `scheduled-job-resiliency.json`
- `transaction-boundary-violation.json`

## Architectural Improvements
- **Explicit Ownership**: Each rule logic is now in its own Java class.
- **Type Safety**: No more generic string parsing in a single massive evaluator.
- **Schema Strictness**: JSONs now must match the `RuleDefinition` POJO structure.

### Standardization (Max Call Depth)
- **Modified** `CallGraph`: Added error logging if recursion depth exceeds 100.
- **Modified** All Rule Evaluators: Standardized `maxCallDepth` to a constant `100` (removed per-rule configuration logic).
- **Modified** All JSON Rules: Removed `"maxCallDepth"` field to enforce global standard.

### Additional Rules Implemented
1. `NestedTransactionPropagationEvaluator` (`NESTED-TX-PROPAGATION-001`): Detects nested transactional calls.
2. Standardized `async-transaction-boundary.json` and `nested-transaction-propagation.json` to v2 schema.

### CallGraph Refactoring
- **Hardcoded Max Depth**: `CallGraph` now enforces a `private static final int MAX_CALL_DEPTH = 100`.
- **API Change**: Removed `maxDepth` parameter from all traversal methods (`findReachableMethods`, `findCallChains`, etc.).
- **Evaluator Updates**: All `RuleEvaluator` classes updated to call the simplified `CallGraph` API.
