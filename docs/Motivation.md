# JVM Hotpath - Runtime Code Execution Analysis for Java

A lightweight Java agent that provides **per-line execution counts** for running Java applications, with live-updating HTML reports. Built to fill the gap left by abandoned tools in the modern Java ecosystem.

## The Problem

### What We Lost

For years, **Cobertura** was the go-to tool for understanding how many times each line of code executed during runtime. It provided invaluable insights:
- Which code paths are actually used in production
- Performance hotspots (lines executed thousands of times)
- Dead code identification
- Runtime behavior analysis beyond simple test coverage

**Cobertura was abandoned in 2015** and doesn't support modern Java (Java 11+, 17, 21).

### What Currently Exists (and Why It's Not Enough)

| Tool | Status | Java 21 Support | Execution Counts | Issue |
|------|--------|-----------------|------------------|-------|
| **Cobertura** | ❌ Abandoned (2015) | ❌ | ✅ | No longer maintained |
| **OpenClover** | ⚠️ Unmaintained | ❌ | ✅ | Fails on Java 21 bytecode |
| **JaCoCo** | ✅ Active | ✅ | ❌ | Only shows coverage %, not counts |
| **JCov** | ✅ Active | ✅ | ✅ | No Maven repo, must build from source, poor docs |
| **IDE-based Coverage** | ✅ Active | ✅ | ⚠️ | Shows counts in IDE only, no export |

**The gap:** No actively maintained, easy-to-use tool provides runtime execution counts for modern Java applications.

## Why Execution Analysis is Not Coverage

There is a fundamental difference between **Code Coverage** and **Execution Frequency Analysis**.

| Metric | Code Coverage (JaCoCo) | Execution Analysis (JVM Hotpath) |
|------|------------------------|---------------------------------------|
| **Goal** | Verification | Understanding |
| **Question** | "Did it run?" | "How much did it run?" |
| **Data Type** | Boolean (Hit/Miss) | Integer (Counter) |
| **Focus** | Test Completeness | Runtime Behavior & Hot-paths |
| **Visual** | Red/Green Gutter | Heatmaps & Magnitude |

### The Real-World Example

Imagine a classic validation line:
```java
if (input == null) throw new IllegalArgumentException();
```

- **JaCoCo says:** "Covered ✓" (because one test passed a null).
- **JVM Hotpath says:** "1,400,000 executions" (revealing that nulls are unexpectedly common in production, or this is a critical loop).

Execution counts reveal insights that simple coverage percentages can't:

### Use Cases

**1. Performance Analysis**
```
Line 42: executed 1 time        ← Setup code
Line 43: executed 50,000 times  ← Hotspot! Optimize this
Line 44: executed 1 time        ← Teardown code
```

**2. Production Behavior Understanding**
- Which error handlers actually fire?
- Which feature flags are used?
- Which code paths dominate real usage?

**3. Dead Code Detection**
- Not just "covered/not covered"
- But "executed once in tests vs. never in production"

**4. Refactoring Confidence**
- See which methods are called frequently before optimizing
- Understand code path frequency before making changes

**5. Testing Gap Analysis**
- Tests might hit a line once (100% coverage!)
- Production hits it 10,000 times (very different story)

### Real-World Example

```java
// JaCoCo says: "Line 15: Covered ✓"
// JVM Hotpath says: "Line 15: executed 47,293 times"
```

One tells you it was executed. The other tells you it's a critical hotspot.

## What This Project Does

### Core Features

1. **Runtime Instrumentation**
    - Uses ASM bytecode instrumentation
    - Zero code changes required
    - Works as a Java agent: `-javaagent:jvm-hotpath-agent.jar`

2. **Per-Line Execution Counts**
    - Tracks every line execution in instrumented classes
    - Thread-safe concurrent counting
    - Minimal performance overhead

3. **Live-Updating HTML Reports**
    - Beautiful, interactive web interface
    - Syntax-highlighted source code
    - Visual heatmaps (green = frequently executed, red = rarely)
    - Live updates while your app runs (optional)

4. **Modern Java Support**
    - Java 11, 17, 21+ compatible
    - Works with any framework (Spring Boot, Micronaut, Quarkus, etc.)
    - Handles generated classes, proxies, lambdas

### What It Looks Like

```
📊 Execution Count Report
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
📂 com.example.service
  📄 DataProcessor.java
     Line 23: 1 execution       [░]
     Line 24: 1,423 executions  [████░]
     Line 25: 47,293 executions [██████████] ← Hotspot!
     Line 26: 892 executions    [███░]
```

## Why We Built This

### The Journey

1. **Started with a need:** Modern Java app (Java 21) needed runtime execution analysis
2. **Tried existing tools:**
    - Cobertura → Doesn't work with Java 21
    - Clover → Fails on modern bytecode
    - JaCoCo → Doesn't show execution counts
    - JCov → No binaries, complex build, poor docs
3. **Built a solution:** This tool, using ASM for instrumentation

### Design Philosophy

- **Zero dependencies in target app** - Agent is self-contained
- **Minimal overhead** - Optimized for production use
- **Easy to use** - Just add `-javaagent` flag
- **Beautiful reports** - Because developers deserve nice tools
- **Open source** - Fill the ecosystem gap for everyone

## How It Compares

### vs. Cobertura (Spiritual Successor)
- ✅ Same per-line execution counts
- ✅ Modern Java support (11, 17, 21+)
- ✅ Better visualizations
- ➕ Live updates
- ➕ Active maintenance

### vs. JaCoCo (Different Purpose)
- JaCoCo: "Did this line execute?" (coverage %)
- JVM Hotpath: "How many times?" (frequency analysis)
- Use JaCoCo for: Test coverage metrics
- Use JVM Hotpath for: Runtime behavior analysis

### vs. JCov (Easier Alternative)
- ✅ Maven Central availability (JCov requires manual build)
- ✅ Simple setup
- ✅ Better documentation
- ✅ Modern web UI
- ➕ Live updates

### vs. Profilers (Different Focus)
- Profilers: CPU time, memory allocation, method duration
- JVM Hotpath: Line-level execution frequency
- Use profilers for: Performance bottlenecks
- Use JVM Hotpath for: Code path analysis

### ❌ This is NOT for:

- **Test coverage metrics** - If you need to report "85% line coverage" to your manager, **use JaCoCo**. This tool is not designed to aggregate test results into a single percentage.
- **Performance profiling** - If you need to see exactly *how many milliseconds* a method took, use a CPU profiler (VisualVM, JProfiler).
- **Production monitoring** - For 24/7 monitoring of throughput/latency, use APM tools (Datadog, New Relic).

### ✅ This IS for:

- **Runtime behavior analysis** - Understanding which code paths are actually "alive" in production.
- **Hot-path investigation** - Finding the specific lines that execute millions of times.
- **Dead code detection** - Finding code that stays at "0" even after weeks of real usage.
- **Refactoring preparation** - Knowing which lines are high-stakes before you touch them.
- **Long-running applications** - Watching counts grow in real-time on services or daemons.

## Technical Approach

### Why ASM?

- **Low-level control** - Direct bytecode manipulation
- **Minimal overhead** - No reflection, no proxies
- **Framework agnostic** - Works with any Java code
- **Mature library** - Used by Spring, Hibernate, etc.

### Architecture

```
┌─────────────────────────────────────┐
│   Your Java Application             │
├─────────────────────────────────────┤
│                                     │
│  ┌──────────────┐                   │
│  │ Original     │  Instrumented     │
│  │ Bytecode     │  at class load    │
│  │              │  ↓                │
│  │ Line 42:     │  ○ Counter++      │
│  │   doWork();  │  ○ doWork();      │
│  └──────────────┘                   │
│                                     │
│         ↓ Counts stored             │
│                                     │
│  ┌──────────────────────────────┐   │
│  │  ExecutionCountStore         │   │
│  │  (thread-safe, in-memory)    │   │
│  └──────────────────────────────┘   │
│                                     │
│         ↓ Periodic flush            │
│                                     │
│  ┌──────────────────────────────┐   │
│  │  HTML Report + JSON data     │   │
│  └──────────────────────────────┘   │
└─────────────────────────────────────┘
```


## The Bigger Picture

This tool exists because **the Java ecosystem has a gap**. As Java evolved (9, 11, 17, 21), older tools were left behind. Modern tools focused on different problems (coverage %, not frequency).

We believe developers deserve:
- ✅ Modern tools for modern Java
- ✅ Insights beyond basic metrics
- ✅ Beautiful, usable interfaces
- ✅ Open source solutions

This project is our contribution to filling that gap.

## Project Status

**Current State:** Fully functional, production-ready for analysis (not recommended for 24/7 production monitoring due to overhead).

**Tested With:**
- Java 11, 17, 21
- Spring Boot 3.x
- Micronaut 4.x
- Plain Java applications
- Multi-threaded applications
- Long-running services

## Get Started

```bash
# Add the agent to your application
java -javaagent:jvm-hotpath-agent.jar=packages=com.yourapp,flushInterval=5 \
     -jar your-application.jar

# Open the generated report
open target/site/execution-report.html
```

See the full [Usage Guide](USAGE.md) for detailed instructions.

## Contributing

We built this because we needed it. If you need it too, let's make it better together.

- 🐛 Found a bug? [Open an issue](issues)
- 💡 Have an idea? [Start a discussion](discussions)
- 🔧 Want to contribute? [Submit a PR](pulls)

## License

[MIT License](LICENSE) - Free to use, modify, and distribute.

## Acknowledgments

- **Cobertura** - Inspiration and spiritual predecessor
- **ASM** - The bytecode manipulation library that makes this possible
- **JaCoCo** - Proof that bytecode instrumentation can be elegant
- The Java community - For identifying this gap and asking for solutions

---

**Built with frustration, determination, and a love for good developer tools.**

*"I just wanted to see how many times my code runs. Why is this so hard in 2026?" - Every Java developer trying to use Cobertura with Java 21*