# ATE - Architecture & Transaction Enforcement Engine

A top-tier static code analysis engine for Java/Spring enterprise codebases focused on **architecture enforcement**, not just code quality.

## Architecture Overview

```
┌─────────────────────────────────────────────────────────────┐
│                    REPOSITORY SCANNER                        │
│  - Discovers .java files                                     │
│  - Reads build metadata (pom.xml, build.gradle)              │
└──────────────────────┬──────────────────────────────────────┘
                       ↓
┌─────────────────────────────────────────────────────────────┐
│                    AST PARSER LAYER                          │
│  - Parses Java syntax → AST                                  │
│  - Extracts annotations (retention-aware)                    │
└──────────────────────┬──────────────────────────────────────┘
                       ↓
┌─────────────────────────────────────────────────────────────┐
│              SOURCE MODEL (CANONICAL)                        │
│                    ⭐ MOST IMPORTANT ⭐                       │
│  - Immutable, fully resolved, indexed                        │
│  - Single source of truth for all analysis                  │
└──────────────────────┬──────────────────────────────────────┘
                       ↓
┌─────────────────────────────────────────────────────────────┐
│                    RULE ENGINE                               │
│  - Loads JSON rule definitions                               │
│  - Queries Source Model (read-only)                          │
│  - Traverses precomputed graphs                              │
└──────────────────────┬──────────────────────────────────────┘
                       ↓
┌─────────────────────────────────────────────────────────────┐
│                    REPORT MODEL                              │
│  - Violations (normalized, fingerprinted)                    │
│  - Severity assignments                                      │
│  - PASS/FAIL determination                                   │
└─────────────────────────────────────────────────────────────┘
```

## Key Design Principles

### 1. **Source Model as Central Component**
- **Immutable**: Built once, never modified
- **Fully Resolved**: All symbols, types, and relationships resolved
- **Indexed**: O(1) lookups for common queries
- **Self-Contained**: No external dependencies after construction

### 2. **Deterministic & Auditable**
- Same input → same output (no randomness)
- Every violation includes full context and traceable paths
- Rules are declarative (JSON), not procedural code

### 3. **Separation of Concerns**
- **Rules are Data**: JSON definitions contain no analysis logic
- **Model is Immutable**: Built once, queried many times
- **Graphs are Precomputed**: Not computed on-demand
- **AI is Advisory**: Explains violations, doesn't enforce rules

### 4. **Static Analysis Only**
- No runtime execution
- No Spring context loading
- No log analysis
- Pure static code analysis

## Core Components

### Model Layer (`com.vidnyan.ate.model`)
- **Type**: Represents classes, interfaces, enums, annotations
- **Method**: Represents methods with metadata (Spring annotations, complexity)
- **Field**: Represents fields with annotations
- **Relationship**: Represents relationships (CALLS, REFERENCES, INHERITS, etc.)
- **SourceModel**: The canonical, immutable model

### Graph Layer (`com.vidnyan.ate.graph`)
- **CallGraph**: Precomputed method call graph (bidirectional)
- **DependencyGraph**: Package-level dependency graph with cycle detection

### Rule Engine (`com.vidnyan.ate.rule`)
- **RuleDefinition**: JSON-serializable rule structure
- **RuleEngine**: detectViolationss rules against Source Model
- **Violation**: Immutable violation representation

### Report Layer (`com.vidnyan.ate.report`)
- **ReportModel**: Final analysis report with violations grouped by severity

## Rule Definition Format

Rules are defined in JSON with the following structure:

```json
{
  "ruleId": "RULE_ID",
  "severity": "BLOCKER|ERROR|WARN|INFO",
  "description": "Human-readable description",
  "query": {
    "type": "graph_traversal|model_query|pattern_match",
    "graph": "call_graph|dependency_graph",
    "pattern": {
      "start": { "annotation": "...", "element": "method" },
      "traverse": { "edge": "calls", "maxDepth": 10 },
      "target": { "annotation": [...] }
    }
  },
  "violation": {
    "message": "Template with {placeholders}",
    "location": "method|callSite|package"
  }
}
```

## Example Rules

### 1. Scheduled Job Resiliency
Detects `@Scheduled` methods that don't use retry/resilience logic.

**File**: `src/main/resources/rules/scheduled-job-resiliency.json`

### 2. Transaction Boundary Violation
Detects remote calls (Feign, RestTemplate) inside `@Transactional` methods.

**File**: `src/main/resources/rules/transaction-boundary-violation.json`

### 3. Circular Dependency
Detects circular dependencies between packages.

**File**: `src/main/resources/rules/circular-dependency.json`

## Usage

### Spring Boot Application

The engine runs as a Spring Boot application using `CommandLineRunner`. All components use dependency injection - no `new` keyword needed!

#### Configuration

Configure via `application.properties`:

```properties
# Path to repository to analyze
ate.analysis.repository-path=/path/to/repository

# Rule files (can be classpath resources or file system paths)
ate.analysis.rule-files[0]=src/main/resources/rules/scheduled-job-resiliency.json
ate.analysis.rule-files[1]=src/main/resources/rules/transaction-boundary-violation.json
ate.analysis.rule-files[2]=src/main/resources/rules/circular-dependency.json
```

Or via `application.yml`:

```yaml
ate:
  analysis:
    repository-path: /path/to/repository
    rule-files:
      - src/main/resources/rules/scheduled-job-resiliency.json
      - src/main/resources/rules/transaction-boundary-violation.json
      - src/main/resources/rules/circular-dependency.json
```

#### Running

```bash
# Using Maven
mvn spring-boot:run

# Or build and run JAR
mvn clean package
java -jar target/ate-0.0.1-SNAPSHOT.jar
```

#### Programmatic Usage (with Spring Context)

```java
@Autowired
private AnalysisEngine analysisEngine;

public void runAnalysis() throws IOException {
    ReportModel report = analysisEngine.analyze();
    
    if (report.getResult() == ReportModel.AnalysisResult.FAIL) {
        // Handle failure
    }
}
```

## Analysis Pipeline

1. **Load Repository**: Scan for `.java` files
2. **Discover Source Files**: Filter and group by compilation unit
3. **Parse Files to AST**: Use JavaParser to build ASTs
4. **Extract Raw Facts**: Extract types, methods, fields, annotations
5. **Build Canonical Source Model**: Resolve symbols, extract relationships
6. **Build Call Graph**: Precompute method call relationships
7. **Build Dependency Graph**: Precompute package dependencies, detect cycles
8. **Index Annotations**: Build annotation → entity indexes
9. **Load Rule Policies**: Parse JSON rule definitions
10. **detectViolations Rules**: Query model/graphs for violations
11. **Collect Violations**: Deduplicate and normalize
12. **Normalize & Fingerprint**: Generate unique IDs for violations
13. **Severity Decision**: Apply severity levels
14. **PASS / FAIL**: Determine final result

## Extending the Engine

### Adding New Rule Types

1. Define rule JSON schema
2. Implement evaluation in `RuleEngine.detectViolationsRule()`
3. Add query type handler

### Adding New Graph Types

1. Create graph builder class in `com.vidnyan.ate.graph`
2. Implement `build(SourceModel)` method
3. Add graph to `RuleEngine` constructor

### Adding New Metadata

1. Extend model entities (Type, Method, Field)
2. Update `SourceModelBuilder` to compute metadata
3. Update rule queries to use new metadata

## Dependencies

- **JavaParser**: AST parsing and symbol resolution
- **Jackson**: JSON rule definition parsing
- **Lombok**: Reduce boilerplate
- **Spring Boot**: Framework (optional, can be used standalone)

## Future Enhancements

- [ ] AI Advisory Layer: Explain violations, assess risk, suggest refactoring
- [ ] Incremental Analysis: Only analyze changed files
- [ ] SARIF Output: Standard format for CI/CD integration
- [ ] More Graph Types: Inheritance graph, annotation graph
- [ ] Method Body Analysis: Extract actual method calls from AST
- [ ] Type Resolution: Full generic type resolution
- [ ] Spring Context Analysis: Detect bean dependencies

## License

[Your License Here]

