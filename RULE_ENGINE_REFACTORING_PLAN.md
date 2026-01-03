# Implementation Plan - Refactor Rule Engine

The goal is to move towards a strict "One Request, One Class" model for rules, ensuring each architectural rule has a dedicated Java implementation handling its logic, while standardizing the JSON definition format.

## User Review Required
> [!IMPORTANT]
> `ArchitecturalRule` will be deleted. `RuleDefinition` will be modified to support the new schema.
> `GraphTraversalEvaluator` will be deprecated, not deleted.

## Proposed Changes

### Core Engine
#### [MODIFY] [RuleEvaluator.java](file:///Users/vikas/Desktop/java/ate/src/main/java/com/vidnyan/ate/rule/evaluator/RuleEvaluator.java)
- Ensure it uses `RuleDefinition`.
- Clean up any unused imports or methods if necessary.

#### [MODIFY] [RuleEngine.java](file:///Users/vikas/Desktop/java/ate/src/main/java/com/vidnyan/ate/rule/RuleEngine.java)
- Ensure it loads rules into `RuleDefinition` objects.

#### [MODIFY] [RuleDefinition.java](file:///Users/vikas/Desktop/java/ate/src/main/java/com/vidnyan/ate/rule/RuleDefinition.java)
- Update fields to match the JSON schema (v2) if not already present.
- Add necessary fields from the deleted `ArchitecturalRule` (like `Result` or refined `Target` if needed). **Wait, I need to check RuleDefinition content first to be precise.** 
*(I will verify the content of RuleDefinition in the next step before finalizing specific field changes, but the direction is set.)*

#### [DELETE] ArchitecturalRule.java
- Delete this class as requested.

#### [MODIFY] [GraphTraversalEvaluator.java](file:///Users/vikas/Desktop/java/ate/src/main/java/com/vidnyan/ate/rule/evaluator/GraphTraversalEvaluator.java)
- Add `@Deprecated` annotation.
- Add JavaDoc indicating it is deprecated and replaced by specific rule evaluators.

### Rule Implementations
Create specific evaluator classes in `com.vidnyan.ate.rule.evaluator` package. Each class implements `RuleEvaluator`.

#### [NEW] [AsyncTransactionBoundaryEvaluator.java](file:///Users/vikas/Desktop/java/ate/src/main/java/com/vidnyan/ate/rule/evaluator/AsyncTransactionBoundaryEvaluator.java)
- Handles `ASYNC-TX-BOUNDARY-001`
- Logic: `@Async` methods must not call `@Transactional`

#### [NEW] [LayeredArchitectureEvaluator.java](file:///Users/vikas/Desktop/java/ate/src/main/java/com/vidnyan/ate/rule/evaluator/LayeredArchitectureEvaluator.java)
- Handles `LAYERED-ARCHITECTURE-001` (was `layered-architecture.json`)
- Logic: Enforce package dependencies (Service !-> Controller)

#### [NEW] [CircularDependencyEvaluator.java](file:///Users/vikas/Desktop/java/ate/src/main/java/com/vidnyan/ate/rule/evaluator/CircularDependencyEvaluator.java)
- Handles `CIRCULAR-DEPENDENCY-001`

#### [NEW] [TransactionalAsyncEvaluator.java](file:///Users/vikas/Desktop/java/ate/src/main/java/com/vidnyan/ate/rule/evaluator/TransactionalAsyncEvaluator.java)
- Handles `TX-CALLS-ASYNC-001`
- Logic: `@Transactional` calling `@Async`

#### [NEW] [IdempotencyEvaluator.java](file:///Users/vikas/Desktop/java/ate/src/main/java/com/vidnyan/ate/rule/evaluator/IdempotencyEvaluator.java)
- Handles `RETRY-IDEMPOTENCY-001`

#### [NEW] [ScheduledJobResiliencyEvaluator.java](file:///Users/vikas/Desktop/java/ate/src/main/java/com/vidnyan/ate/rule/evaluator/ScheduledJobResiliencyEvaluator.java)
- Handles `SCHEDULED-RESILIENCY-001`

#### [NEW] [TransactionBoundaryEvaluator.java](file:///Users/vikas/Desktop/java/ate/src/main/java/com/vidnyan/ate/rule/evaluator/TransactionBoundaryEvaluator.java)
- Handles `TX-BOUNDARY-001`

### Configuration
#### [MODIFY] JSON Files
- Rewrite all files in `src/main/resources/rules/` to match `ArchitecturalRule` schema (v2).
- Ensure IDs match the evaluators.

## Verification Plan

### Automated Tests
- Run `mvn test` to ensure no regressions.
- Create a test class `RuleEngineRefactoringTest` (or similar) that loads the new rules and mocks `SourceModel` to verify each evaluator works correctly.

### Manual Verification
- Run the analysis engine against the current codebase (if possible via existing main class) and check if rules are triggered.
