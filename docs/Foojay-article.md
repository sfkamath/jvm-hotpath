# Runtime Code Analysis in the Age of Vibe Coding

In the era of **vibe coding**—where large amounts of code are introduced or refactored in short
bursts, often with the help of LLMs—you need immediate feedback on how new logic actually
executes. Not comprehensive analysis. Not nanosecond-precise timing. Just a quick confirmation that
your loops aren't spinning 10,000x more than they should.

Traditional profilers are powerful but feel like overkill for quick validation. They operate at
method-level granularity, require switching to a separate tool to analyze results, and introduce
overhead (2-10%+ depending on mode) that makes them less suitable for continuous, live feedback
during rapid iteration.

**jvm-hotpath** is a lightweight Java agent built for this workflow. It surfaces per-line execution
counts directly in your source code, showing you exactly which lines run and how often—while your
application runs.

## The Gap in Java Tooling

Years ago, when I inherited a system I barely understood, I hacked Cobertura—a coverage tool—to
use it as a runtime analysis tool. By instrumenting the application and manually exercising
specific behaviors, I could observe execution counts after the fact. It gave me a runtime-shaped
mental map of the codebase and—crucially—an entry point into making feature changes with some
confidence.

Cobertura has been abandoned since 2015 and doesn't support modern Java. Since then, no actively
maintained tool has filled this specific gap:

- **Coverage tools** (JaCoCo) tell you if a line executed at least once
- **Profilers** tell you where CPU time is spent
- **What's missing** is a simple way to see execution frequency under real conditions

Modern Java tooling has moved in different directions, but the idea stuck with me. I spent some
time evaluating what was available today. OpenClover doesn't support modern Java versions beyond
17. I even attempted to try JCov, but quickly decided that building it from source wasn't worth
the effort given the poor documentation and lack of Maven Central binaries. IntelliJ can surface
execution counts internally (though you have to hover to see them), but they're buried inside a
proprietary .ic format and not exposed in a way that's usable outside the IDE.

At some point in that process, Claude put the choice quite bluntly:

"Do you want me to help you build JCov from source, or would you rather I create a simple custom
execution counter for you?"

That question effectively decided the direction.

## A Real-World Bug

This tool was born during a high-velocity vibe coding session where I was refactoring a core
processing engine. Standard profilers missed the following bug because the system didn't *feel*
slow yet:

**The Bug:** A `.filter(r -> r.isDuplicate())` call was being executed 19 million times in 15 seconds.  
**The Problem:** Each call was ~50 nanoseconds—too fast for sampling profilers to notice.  
**The Impact:** O(N²) instead of O(1) was hiding in plain sight.

The filter was sitting inside a loop instead of being evaluated once. Classic mistake, but
invisible to traditional tools.

Execution counts made it obvious: seeing "19,147,293 executions" next to a single line removed all
ambiguity. No timing data required, no interpretation needed.

## The Key Insight: Frequency ≠ Resource Consumption

Java profilers focus on **resource consumption**—CPU time, memory allocation, thread contention.  
jvm-hotpath shows **how many times code runs** (frequency).

In modern Java:
- JIT compilation makes methods fast
- The bottleneck is often algorithmic (O(N) vs O(1))
- Logic errors create millions of unnecessary calls
- Sampling profilers miss methods that execute quickly but frequently

**It's a "Logic X-Ray" not a "Resource Monitor."**

## How It Works

jvm-hotpath is a Java agent that instruments bytecode at class-load time using ASM. It inserts a
counter before each executable line—no sampling, no timing, just frequency.

The overhead is low enough for normal development runs. The collected data is written to an
interactive HTML report that refreshes while your application runs, showing:
- Syntax-highlighted source code
- Execution counts next to each line
- A global heatmap that makes hot paths stand out visually
- Live updates without needing a web server

**See it in action:**

https://github.com/user-attachments/assets/cc89451b-a41f-491e-a1f6-8e87328979c0

## Getting Started

### Maven Plugin (Recommended)

Add the plugin to your `pom.xml`:

```xml
<plugin>
    <groupId>io.github.sfkamath</groupId>
    <artifactId>jvm-hotpath-maven-plugin</artifactId>
    <version>0.2.1</version>
    <executions>
        <execution>
            <goals>
                <goal>prepare-agent</goal>
            </goals>
        </execution>
    </executions>
    <configuration>
        <!-- Auto-flush report every 5 seconds -->
        <flushInterval>5</flushInterval>
    </configuration>
</plugin>
```

Then run your application with the agent active:
```bash
mvn jvm-hotpath:prepare-agent exec:exec -Pinstrument -Dexec.mainClass="com.example.Main"
```

The report will be generated at `target/site/jvm-hotpath/execution-report.html`.

### Manual Agent Usage

If you prefer direct control:

```bash
java -javaagent:jvm-hotpath-agent.jar=packages=com.example,sourcepath=src/main/java,flushInterval=5 -jar your-app.jar
```

**Key Parameters:**
- `packages` - Comma-separated list of packages to instrument (e.g., `com.myapp`)
- `sourcepath` - Path to source files for code overlay
- `flushInterval` - Seconds between report refreshes (0 = no auto-flush)
- `verbose` - Print instrumentation details with clickable file URLs

## Beyond Performance: Dead Code and Cognitive Load

Execution counts make it easy to spot dead code, rarely used branches, and features that exist
largely for historical reasons. They also reduce cognitive load: when you know which parts of a
system actually run, it becomes much easier to reason about changes, refactor with confidence, or
decide what not to think about yet.

For anyone working quickly with AI-assisted tools, that kind of clarity is invaluable.

## What Makes This Different

- **Zero timing overhead** - Just counts, no nanosecond measurements
- **Counts every execution** - No sampling, no missing fast methods
- **LLM-friendly output** - JSON reports you can pipe to Claude/GPT for analysis
- **Live updates** - Watch counts grow in real-time without a web server
- **Modern Java** - Supports Java 11-24, works with Spring Boot, Micronaut

## A Note on How This Was Built

The first prototype came out of AI-assisted vibe coding, primarily with Claude. From there, I
iterated using a mix of manual work and help from Codex and Gemini, validating everything against
real JVM workloads.

The tools accelerated exploration, but the motivation and direction came from hands-on use in real
codebases.

## Where This Is Going

This is my first open-source release, and I'm deliberately keeping the scope small. There are
obvious next steps—Gradle improvements, better exclusion controls, broader framework testing.

But the real question is simpler: **does this help you understand your codebase faster and with
more confidence?**

---

**Project:** [github.com/sfkamath/jvm-hotpath](https://github.com/sfkamath/jvm-hotpath)  
**Documentation:** [Full README](https://github.com/sfkamath/jvm-hotpath/blob/main/README.md)  
**Motivation:** [Deep dive into the
why](https://github.com/sfkamath/jvm-hotpath/blob/main/docs/Motivation.md)
