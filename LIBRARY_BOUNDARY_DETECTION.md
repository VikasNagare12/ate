# Library Boundary Detection in Call Graph

## Problem

When tracing call chains in enterprise applications, you don't want to trace into external libraries like:
- **Spring Framework** - `JdbcTemplate`, `RestTemplate`, `TransactionTemplate`
- **Hibernate** - `Session.save()`, `EntityManager.persist()`
- **Java Standard Library** - `List.add()`, `Map.put()`
- **Third-party libraries** - Apache Commons, Jackson, etc.

**Without boundary detection:**
```
UserService.createUser()
  → UserRepository.save(User)
    → JdbcTemplate.update(String, Object[])
      → PreparedStatement.executeUpdate()
        → ConnectionImpl.prepareStatement()
          → ... (hundreds of internal JDBC calls)
```

**With boundary detection (CORRECT):**
```
UserService.createUser()
  → UserRepository.save(User)
    → JdbcTemplate.update(String, Object[])  ← STOPS HERE (library boundary)
```

## Solution

The CallGraph now automatically detects library boundaries:

### How It Works

```java
public boolean isLibraryMethod(String methodSignature) {
    // If method is not in our SourceModel, it's a library method
    return sourceModel.getMethod(methodSignature) == null;
}
```

**Key Insight:** Your repository code is in the `SourceModel`. Library code is NOT. So any method not in the SourceModel is treated as a **leaf node** (end of chain).

## API Changes

All call chain methods now **stop at library boundaries by default**:

### 1. findCallChains()

```java
// Stops at library boundaries (DEFAULT)
List<List<String>> chains = callGraph.findCallChains("UserService#createUser()", 5);
// Returns: [
//   ["UserService#createUser()", "UserRepository#save(User)", "JdbcTemplate.update(?,?)"]
// ]

// Include library calls (if needed for debugging)
List<List<String>> chainsWithLibs = callGraph.findCallChains("UserService#createUser()", 5, false);
// Returns: [
//   ["UserService#createUser()", "UserRepository#save(User)", "JdbcTemplate.update(?,?)", 
//    "PreparedStatement.executeUpdate()", "ConnectionImpl.prepareStatement()", ...]
// ]
```

### 2. findCallChainsToTarget()

```java
// Stops at library boundaries (DEFAULT)
List<List<String>> paths = callGraph.findCallChainsToTarget(
    "UserController#createUser(UserDTO)",
    "JdbcTemplate.update(?,?)",
    10
);

// Include library calls
List<List<String>> pathsWithLibs = callGraph.findCallChainsToTarget(
    "UserController#createUser(UserDTO)",
    "PreparedStatement.executeUpdate()",
    10,
    false  // stopAtLibraries = false
);
```

### 3. findReachableMethods()

```java
// Stops at library boundaries (DEFAULT)
Set<String> reachable = callGraph.findReachableMethods("UserService#createUser()", 5);
// Returns: ["UserRepository#save(User)", "JdbcTemplate.update(?,?)", "AuditService#log(String)"]

// Include library calls
Set<String> reachableWithLibs = callGraph.findReachableMethods("UserService#createUser()", 5, false);
// Returns: [..., "PreparedStatement.executeUpdate()", "ConnectionImpl.prepareStatement()", ...]
```

### 4. getCallees() with filtering

```java
// Get direct callees (includes libraries)
List<String> allCallees = callGraph.getCallees("UserRepository#save(User)");
// Returns: ["JdbcTemplate.update(?,?)", "AuditService#log(String)"]

// Get only repository callees (exclude libraries)
List<String> repoCallees = callGraph.getCallees("UserRepository#save(User)", true);
// Returns: ["AuditService#log(String)"]  // JdbcTemplate filtered out
```

## Real-World Example

### Scenario: Transaction Boundary Analysis

**Your Code:**
```java
@Service
public class OrderService {
    @Transactional
    public void createOrder(OrderDTO dto) {
        Order order = orderMapper.toEntity(dto);
        orderRepository.save(order);  // Calls JdbcTemplate internally
        emailService.sendConfirmation(order);  // Your code
    }
}

@Repository
public class OrderRepository {
    public void save(Order order) {
        jdbcTemplate.update("INSERT INTO orders ...", order);  // Library call
    }
}
```

**Call Chain Analysis:**
```java
List<List<String>> chains = callGraph.findCallChains(
    "OrderService#createOrder(OrderDTO)", 
    5
);

// Result (library boundary detection):
// [
//   ["OrderService#createOrder(OrderDTO)", 
//    "OrderMapper#toEntity(OrderDTO)", 
//    "Order.<init>(?,?)"],
//   
//   ["OrderService#createOrder(OrderDTO)", 
//    "OrderRepository#save(Order)", 
//    "JdbcTemplate.update(?,?)"],  ← STOPS HERE
//   
//   ["OrderService#createOrder(OrderDTO)", 
//    "EmailService#sendConfirmation(Order)",
//    "JavaMailSender.send(?)"]  ← STOPS HERE
// ]
```

**Without library boundary detection, you'd get:**
```
Hundreds of internal Spring/JDBC calls that you don't care about!
```

## Benefits

### 1. Performance
- **Faster analysis** - Don't traverse thousands of library methods
- **Less memory** - Fewer chains to store
- **Focused results** - Only see your code

### 2. Clarity
- **Readable chains** - `a→b→c→JdbcTemplate` instead of `a→b→c→d→e→f→g→...`
- **Actionable insights** - Focus on your code, not library internals
- **Better violations** - Rules detect issues in YOUR code

### 3. Accuracy
- **Correct transaction boundaries** - Know exactly where your code ends
- **Proper async boundaries** - Library async calls don't confuse analysis
- **Real retry scope** - See what YOUR code retries, not library internals

## Common Library Patterns Detected

The following library calls will be treated as leaf nodes:

### Spring Framework
```
JdbcTemplate.update(?,?)
JdbcTemplate.query(?,?)
RestTemplate.exchange(?,?)
TransactionTemplate.execute(?)
RedisTemplate.opsForValue()
KafkaTemplate.send(?,?)
```

### JPA/Hibernate
```
EntityManager.persist(?)
EntityManager.merge(?)
Session.save(?)
Session.update(?)
```

### Java Standard Library
```
List.add(?)
Map.put(?,?)
Stream.collect(?)
```

### Third-party Libraries
```
ObjectMapper.writeValueAsString(?)
HttpClient.execute(?)
```

## When to Include Library Calls

Use `stopAtLibraries = false` when:

1. **Debugging** - Need to see full execution path
2. **Performance analysis** - Identify slow library calls
3. **Security auditing** - Trace data flow into libraries

```java
// Debug mode - see everything
List<List<String>> fullChains = callGraph.findCallChains(method, 10, false);
System.out.println("Full execution path:");
fullChains.forEach(chain -> System.out.println(CallGraph.formatCallChain(chain)));
```

## Impact on Rules

All existing rules automatically benefit from library boundary detection:

### Before (Without Boundary Detection)
```json
{
  "ruleId": "ASYNC_TRANSACTION_BOUNDARY",
  "traverse": { "maxDepth": 5 },
  "target": { "mustNotReach": [{ "annotation": "Transactional" }] }
}
```
**Problem:** Might traverse into Spring's internal async executor threads

### After (With Boundary Detection)
```json
{
  "ruleId": "ASYNC_TRANSACTION_BOUNDARY",
  "traverse": { "maxDepth": 5 },
  "target": { "mustNotReach": [{ "annotation": "Transactional" }] }
}
```
**Solution:** Automatically stops at library boundaries, only checks YOUR code

## Summary

✅ **Automatic** - Library boundary detection is ON by default  
✅ **Smart** - Methods not in SourceModel are treated as leaf nodes  
✅ **Configurable** - Can disable with `stopAtLibraries = false`  
✅ **Performance** - Dramatically faster for large codebases  
✅ **Clarity** - Call chains show only YOUR code flow  

**Example:**
```
Your Code:  a → b → c → d (JdbcTemplate)  ← STOPS HERE
Library:                  ↓
                         e → f → g → ... (ignored)
```

This is exactly what you need for enterprise architecture analysis with 25 years of experience - focus on YOUR code boundaries, not library internals!
