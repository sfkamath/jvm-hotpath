# Runtime Code Analysis in the Age of Vibe Coding

In the era of **vibe coding**—where large amounts of code are introduced or refactored in short bursts, often with the help of LLMs—you need immediate feedback on how new logic actually executes. Not comprehensive analysis. Not nanosecond-precise timing. Just a quick confirmation that your loops aren't spinning 10,000x more than they should.

However, traditional profilers can feel like overkill for quick validation. In addition, they present results at method/stack granularity and require context-switching to interpret. They also introduce overhead, ranging from negligible (e.g., JFR/sampling) to noticeable (call tracing/instrumentation). As a result, they are less convenient as always-on feedback during rapid iteration.

**jvm-hotpath** is a lightweight Java agent built for this workflow. It surfaces per-line execution counts directly in your source code, showing you exactly which lines run and how often—while your application runs.

## What Makes This Different

**Zero timing overhead.** Just counts, no nanosecond measurements.

**Counts every execution.** No sampling, no missed fast methods.

**LLM-friendly output.** JSON reports you can pipe to an LLM for analysis.

**Live updates.** JSONP polling lets you watch counts update live. No server is needed.

**Modern Java.** Tested in CI on Java 11, 17, 21, 23, and 24. It also works with Spring Boot and Micronaut.

## The Gap in Java Tooling

### The Original Problem

The immediate pain is simple: code arrives faster than you can build a mental model of it. Years ago, I faced the same core problem in an inherited system. So I hacked Cobertura—a coverage tool—to use it as a runtime analysis tool. By instrumenting the app and exercising specific behaviors, I could observe execution counts after the fact. As a result, I got a runtime-shaped mental map of the codebase—and an entry point for making changes with confidence.

After all, static analysis tells you what *could* execute. Tests tell you what *should* execute. What I needed was to see what *does* execute under real workloads.

### Why Existing Tools Don't Fit

Cobertura's last release was in 2015, so it doesn't fit modern Java toolchains. Since then, no widely adopted, actively maintained tool has focused on live per-line execution frequency.

**Coverage tools** (e.g., JaCoCo) track whether code executed, not how many times. **Profilers** show where CPU time goes. Neither one shows you execution frequency under real conditions.

### How I Ended Up Building This

Modern Java tooling has moved in different directions, but the idea stuck with me. So I evaluated what was available. For instance, OpenClover's "full support" line is Java 17, with newer versions listed as experimental. Similarly, JCov exists as an OpenJDK CodeTools project, but setup is old-school. In short, there's no simple "pull a jar from Maven Central and go" path. IntelliJ's built-in coverage is excellent for coverage, and it stores run data as IDE coverage suites (e.g., .ic). However, it's still an IDE-centric workflow. In short, it's not something you can reuse in CI artifacts or share as a standalone live report.

After an hour of dead ends, Claude cut to the chase:

> "Do you want me to help you build JCov from source, or would you rather I create a simple custom execution counter for you?"

Ultimately, that question decided the direction.

## A Real-World Bug

### How the Bug Appeared

This tool was born during a high-velocity vibe coding session. Specifically, I was refactoring a core processing engine. Standard profilers missed this bug. The system didn't *feel* slow yet:

**The Bug:** A `.filter(r -> r.isDuplicate())` call was executing 19 million times in 15 seconds.
**The Problem:** Each call was ~50 nanoseconds—easy for sampling profilers to under-sample.
**The Impact:** O(N²) instead of O(1) was hiding in plain sight.

### Why It Was Hard to Spot

In other words, the filter was sitting inside a loop instead of being evaluated once. It's a classic mistake. Yet it's invisible to traditional tools. Instead, I wanted immediate runtime visibility into what was actually running.

Execution counts made it obvious. For example, seeing "19,147,293 executions" next to a single line removed all ambiguity. No timing data was required, and no interpretation was needed.

## The Key Insight: Frequency ≠ Resource Consumption

Java profilers focus on **resource consumption**: CPU time, memory allocation, thread contention. jvm-hotpath, by contrast, shows **how many times code runs** (frequency).

In modern Java, this distinction matters. For instance, JIT compilation makes individual calls fast. So the bottleneck is often algorithmic—O(N) vs O(1). Also, logic errors can create millions of unnecessary calls. Furthermore, sampling profilers are statistical. Moreover, very short but frequent work is easy to under-sample.

**It's a "Logic X-Ray," not a "Resource Monitor."**

## How It Works

### Instrumentation

jvm-hotpath is a Java agent that instruments bytecode at class-load time using ASM. Specifically, it inserts a counter before each executable line. Indeed, there's no sampling, no timing—just frequency.

As a result, the overhead is low enough for normal development runs.

### The Report

The collected data is written to an interactive HTML report that refreshes while your app runs. Specifically, it shows syntax-highlighted source code with execution counts next to each line. In addition, a global heatmap makes hot paths stand out visually.

JSONP-powered polling lets you open the report directly from disk (`file://`) and watch it update live. No server is needed.

Notably, the narrow focus is intentional. There are no flame graphs, no dashboards, no post-hoc traces—just line-level execution frequency mapped onto source code.

### Machine-Readable Output

The agent also writes `execution-report.json`. Therefore, it gives you a machine-readable artifact you can feed into CI steps or LLM-based tools.

**See it in action:**

https://github.com/user-attachments/assets/cc89451b-a41f-491e-a1f6-8e87328979c0

## Getting Started

### Maven Plugin (Recommended)

Add the plugin to your `pom.xml`:

```xml
<plugin>
    <groupId>io.github.sfkamath</groupId>
    <artifactId>jvm-hotpath-maven-plugin</artifactId>
    <version>0.2.4</version>
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
mvn -Pinstrument jvm-hotpath:prepare-agent exec:exec
```

For `exec:exec`, you need a main class. Pass `-Dexec.mainClass=...` or configure `exec.mainClass` in `pom.xml`.

The report is generated at `target/site/jvm-hotpath/execution-report.html`.

For multi-module projects or generated code (OpenAPI/MapStruct), the plugin can merge multiple source roots into one report. Also, you can pass dependency source archives directly via `sourcepath`.

### Manual Agent Usage

If you prefer direct control, run:

```bash
java -javaagent:jvm-hotpath-agent.jar=packages=com.example,sourcepath=src/main/java,flushInterval=5 -jar your-app.jar
```

**Key parameters:** `packages` sets which packages to instrument. `sourcepath` points to source roots or archives (`.jar`, `.zip`). `flushInterval` controls seconds between report refreshes (0 = no auto-flush). `verbose` prints instrumentation details with clickable file URLs.

### Standalone Report Regeneration

If you already have `execution-report.json` from CI, you can regenerate the HTML without rerunning the application:

```bash
java -jar jvm-hotpath-agent.jar --data=target/site/jvm-hotpath/execution-report.json --output=target/site/jvm-hotpath/new-report.html
```

### What This Is Not

It is not a coverage percentage tool—use JaCoCo for that. Nor is it a CPU timing profiler—use JFR or async-profiler instead. Finally, it is not a 24/7 production monitoring system.

## Beyond Performance: Dead Code and Cognitive Load

Execution counts make it easy to spot dead code and rarely used branches. Moreover, they surface features that exist largely for historical reasons. Furthermore, they reduce cognitive load. When you know which parts actually run, it becomes much easier to reason about changes, refactor with confidence, or decide what not to think about yet.

Indeed, for anyone working quickly with AI-assisted tools, that kind of clarity is invaluable.

## A Note on How This Was Built

The first prototype came out of AI-assisted vibe coding, primarily with Claude. Subsequently, I iterated with a mix of manual work and help from Codex and Gemini. I also validated everything against real JVM workloads.

Overall, the tools accelerated exploration. Even so, the motivation and direction came from hands-on use in real codebases.

## Where This Is Going

There are obvious next steps—Gradle improvements, better exclusion controls, broader framework testing. For now, though, I'm deliberately keeping the scope small. Indeed, this is my first open-source release.

The real question is simpler: **does this help you understand your codebase faster and with more confidence?**

---

**Project:** [github.com/sfkamath/jvm-hotpath](https://github.com/sfkamath/jvm-hotpath)  
**Documentation:** [Full README](https://github.com/sfkamath/jvm-hotpath/blob/main/README.md)  
**Motivation:** [Deep dive into the why](https://github.com/sfkamath/jvm-hotpath/blob/main/docs/Motivation.md)