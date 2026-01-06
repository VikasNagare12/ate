# ATE - Architectural Transaction Engine

> Static code analysis engine for detecting architectural anti-patterns in Java/Spring applications.

## 🎯 Overview

ATE analyzes Java/Spring codebases to detect architectural anti-patterns that can cause:
- **Transaction safety issues** - Remote calls inside transactions
- **Async safety issues** - Transaction context loss in async methods  
- **Retry safety issues** - Database operations without retry logic
- **Circular dependencies** - Package-level dependency cycles

## 🏗️ Architecture

Clean Architecture (Hexagonal) with clear separation of concerns:

```
com.vidnyan.ate/
├── domain/              # Pure domain logic (no frameworks)
│   ├── model/           # Immutable records: TypeEntity, MethodEntity, etc.
│   ├── graph/           # CallGraph, DependencyGraph, CallEdge
│   └── rule/            # RuleDefinition, Violation, RuleEvaluator
│
├── application/         # Use cases and ports
│   ├── port/in/         # Primary ports (AnalyzeCodeUseCase)
│   ├── port/out/        # Secondary ports (SourceCodeParser, TypeResolver)
│   └── service/         # Application services
│
├── adapter/             # Framework implementations
│   ├── in/cli/          # CLI runner
│   └── out/             # Parsers, evaluators, repositories
│       ├── parser/      # JavaParserAdapterV2 with SymbolSolver
│       ├── evaluator/   # Rule evaluators
│       ├── rule/        # FileSystemRuleRepository
│       └── ai/          # MockAIAdvisor
│
└── config/              # Spring configuration
```

## 🚀 Quick Start

### Prerequisites
- Java 21+
- Maven 3.8+

### Run Analysis

```bash
# Analyze a project
./mvnw spring-boot:run \
  -Dspring-boot.run.jvmArguments="-Date.analyze.path=/path/to/java/src"
```

### Build

```bash
./mvnw clean package
java -jar target/ate-0.0.1-SNAPSHOT.jar -Date.analyze.path=/path/to/src
```

## 📋 Rules

| Rule ID | Name | Severity | Description |
|---------|------|----------|-------------|
| `TX-BOUNDARY-001` | No Remote Calls Inside Transaction | BLOCKER | Detects HTTP/messaging calls inside @Transactional methods |
| `ASYNC-TX-001` | Async Transaction Context Loss | ERROR | Detects @Async methods with @Transactional |
| `JDBC-RETRY-001` | JDBC Without Retry | WARN | Detects database calls without @Retryable |
| `CIRCULAR-DEP-001` | Circular Package Dependency | ERROR | Detects package-level dependency cycles |

### Custom Rules

Add JSON rule definitions to `src/main/resources/rules/`:

```json
{
  "id": "MY-RULE-001",
  "name": "My Custom Rule",
  "description": "Description of what the rule detects",
  "severity": "ERROR",
  "category": "CUSTOM",
  "detection": {
    "entryPoints": {
      "annotations": ["MyAnnotation"],
      "types": [],
      "methodPatterns": []
    },
    "sinks": {
      "types": ["com.example.DangerousClass"],
      "annotations": [],
      "methodPatterns": []
    }
  },
  "remediation": {
    "quickFix": "How to fix this issue",
    "explanation": "Why this is a problem",
    "references": []
  }
}
```

## 🔧 Components

### Domain Layer

**Domain Model (Immutable Records)**
- `TypeEntity` - Classes, interfaces, enums, records
- `MethodEntity` - Methods with parameters, annotations
- `FieldEntity` - Class fields
- `Relationship` - Connections between code elements
- `SourceModel` - Aggregate root with query methods

**Graph Layer**
- `CallGraph` - Method call relationships with traversal
- `DependencyGraph` - Package-level dependencies with cycle detection
- `CallEdge` - Call metadata (type, location, resolved FQN)

**Rule Layer**
- `RuleDefinition` - Rule configuration with detection/remediation
- `Violation` - Detected issues with location and call chain
- `EvaluationResult` - Rule evaluation outcome
- `RuleEvaluator` - Interface for rule implementations

### Application Layer

**Ports (Interfaces)**
- `AnalyzeCodeUseCase` - Primary entry point
- `SourceCodeParser` - Parse source files to domain model
- `TypeResolver` - Resolve type FQNs
- `RuleRepository` - Load rule definitions
- `AIAdvisor` - AI-powered recommendations

### Adapter Layer

**Parsers**
- `JavaParserAdapterV2` - Uses JavaSymbolSolver for ~95% type resolution

**Evaluators**
- `PathReachabilityEvaluator` - Generic entry→sink detection
- `TransactionBoundaryEvaluatorV2` - Remote calls in @Transactional
- `AsyncTransactionEvaluatorV2` - @Async + @Transactional conflicts
- `JdbcRetrySafetyEvaluatorV2` - JDBC without retry
- `CircularDependencyEvaluatorV2` - Package cycles

## 📊 Type Resolution

ATE uses JavaParser's SymbolSolver for accurate type resolution:

| Resolution Method | Accuracy |
|-------------------|----------|
| ~~Manual lookup~~ | ~60% |
| **SymbolSolver** | **~95%** |

The SymbolSolver automatically resolves:
- JDK classes (java.*, javax.*)
- Application source code
- Imported types
- Chained method calls
- Field types

## 🔌 Extensibility

### Add New Evaluator

```java
@Component
public class MyEvaluator implements RuleEvaluator {
    
    @Override
    public boolean supports(RuleDefinition rule) {
        return "MY-RULE-001".equals(rule.id());
    }
    
    @Override
    public EvaluationResult evaluate(EvaluationContext context) {
        // Access domain model
        SourceModel model = context.sourceModel();
        CallGraph callGraph = context.callGraph();
        
        // Find violations
        List<Violation> violations = // ...
        
        return EvaluationResult.success(rule.id(), violations, duration, nodesAnalyzed);
    }
}
```

### Add AI Advisor

Implement `AIAdvisor` interface and replace `MockAIAdvisor`:

```java
@Component
@Primary
public class OpenAIAdvisor implements AIAdvisor {
    @Override
    public AdviceResult getAdvice(List<Violation> violations) {
        // Call OpenAI/Anthropic API
    }
}
```

## 📁 Project Structure

```
src/main/java/com/vidnyan/ate/
├── AteApplication.java                    # Spring Boot entry
├── config/
│   └── AteConfiguration.java              # Bean configuration
├── domain/
│   ├── model/                             # 8 records
│   ├── graph/                             # 3 classes
│   └── rule/                              # 5 classes
├── application/
│   ├── port/in/                           # 1 interface
│   ├── port/out/                          # 4 interfaces
│   └── service/                           # 1 service
└── adapter/
    ├── in/cli/                            # 1 CLI runner
    └── out/
        ├── parser/                        # 3 classes
        ├── evaluator/                     # 5 evaluators
        ├── rule/                          # 1 repository
        └── ai/                            # 1 advisor

src/main/resources/
└── rules/                                 # 4 JSON rule definitions
```

## 🛠️ Tech Stack

- **Java 21** - Records, pattern matching, virtual threads
- **Spring Boot 4.0** - DI, configuration
- **JavaParser 3.26** - AST parsing
- **JavaSymbolSolver** - Type resolution
- **Lombok** - Boilerplate reduction
- **Jackson** - JSON rule parsing

## 📝 License

MIT License

## 🤝 Contributing

1. Fork the repository
2. Create feature branch: `git checkout -b feature/my-feature`
3. Commit changes: `git commit -m 'Add my feature'`
4. Push to branch: `git push origin feature/my-feature`
5. Open Pull Request
