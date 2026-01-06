# Java 21 & Spring Boot 4 Configuration

## ✅ Configuration Verified

### Java 21
- **Java Version**: 21 (configured in `pom.xml`)
- **Maven Compiler Plugin**: Explicitly set to Java 21 (source, target, release)
- **Features Used**:
  - ✅ Switch expressions with `->` and `yield`
  - ✅ `var` keyword for local variable type inference
  - ✅ Records (`DepthNode` in `CallGraph`)
  - ✅ `.toList()` method (Java 21 Collections API)
  - ✅ Pattern matching in switch expressions

### Spring Boot 4
- **Version**: 4.0.1 (configured in `pom.xml`)
- **Jakarta EE**: All imports use `jakarta.*` instead of `javax.*`
  - ✅ `jakarta.annotation.PostConstruct`
  - ✅ Spring Boot 4 uses Jakarta EE 9+ namespace
- **Dependencies**: Managed by Spring Boot parent POM

## Code Examples Using Java 21 Features

### 1. Switch Expressions (RuleEngine.java)
```java
return switch (queryType) {
    case "graph_traversal" -> detectViolationsGraphTraversalRule(rule);
    case "model_query" -> detectViolationsModelQueryRule(rule);
    case "pattern_match" -> detectViolationsPatternMatchRule(rule);
    default -> {
        log.warn("Unknown query type: {}", queryType);
        yield List.of();
    }
};
```

### 2. Records (CallGraph.java)
```java
private record DepthNode(String method, int depth) {}
```

### 3. Var Keyword (AnalysisEngine.java)
```java
var resource = getClass().getClassLoader().getResource(path);
```

### 4. Modern Collections API (AnalysisEngine.java)
```java
List<Path> ruleFiles = analysisProperties.getRuleFiles().stream()
    .map(...)
    .toList(); // Java 21 - no need for .collect(Collectors.toList())
```

## Maven Configuration

```xml
<properties>
    <java.version>21</java.version>
</properties>

<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-compiler-plugin</artifactId>
    <configuration>
        <source>21</source>
        <target>21</target>
        <release>21</release>
        ...
    </configuration>
</plugin>
```

## Spring Boot 4 Features

- ✅ Jakarta EE 9+ namespace (`jakarta.*`)
- ✅ Native compilation support (GraalVM)
- ✅ Virtual threads support (Project Loom)
- ✅ Enhanced observability
- ✅ Improved performance

## Verification Checklist

- [x] Java 21 configured in `pom.xml`
- [x] Maven compiler plugin set to Java 21
- [x] Spring Boot 4.0.1 parent POM
- [x] All imports use `jakarta.*` (no `javax.*`)
- [x] Code uses Java 21 features (switch expressions, records, var)
- [x] No deprecated APIs
- [x] Lombok compatible with Java 21
- [x] JavaParser compatible with Java 21

## Running the Application

```bash
# Verify Java version
java -version  # Should show Java 21

# Build with Maven
mvn clean compile

# Run Spring Boot application
mvn spring-boot:run
```

## Notes

- Spring Boot 4 requires Java 17+ (we're using Java 21 ✅)
- All Jakarta EE dependencies are automatically managed by Spring Boot parent
- Java 21 LTS provides better performance and new language features
- Records are perfect for immutable data structures (like `DepthNode`)

