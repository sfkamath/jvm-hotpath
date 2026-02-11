# Runtime Code Analysis in the Age of Vibe Coding

In the era of **vibe coding**—where large amounts of code are introduced or refactored in short
bursts, often with the help of LLMs—you need immediate feedback on how new logic actually
executes. Not comprehensive analysis. Not nanosecond-precise timing. Just a quick confirmation that
your loops aren't spinning 10,000x more than they should.

Traditional profilers are powerful but can feel like overkill for quick validation. They usually
present results at method/stack granularity and require context-switching to interpret. They also
introduce overhead that ranges from negligible (e.g., JFR / sampling) to noticeable (call tracing
/ instrumentation), which makes them less convenient as always-on feedback during rapid iteration.

**jvm-hotpath** is a lightweight Java agent built for this workflow. It surfaces per-line execution
counts directly in your source code, showing you exactly which lines run and how often—while your
application runs.

## The Gap in Java Tooling

The immediate pain today is vibe coding producing large chunks of new/refactored code faster than
you can build a full mental model. Years ago I hit the same core problem in an inherited system
and hacked Cobertura—a coverage tool—to use it as a runtime analysis tool. By instrumenting the
application and manually exercising specific behaviors, I could observe execution counts after the
fact. It gave me a runtime-shaped mental map of the codebase and—crucially—an entry point into
making feature changes with some confidence.

Static analysis tells you what could execute. Tests tell you what should execute. What I needed was
to see what does execute when the system is alive under real workloads.

Cobertura's last release was in 2015, and it doesn't fit modern Java toolchains/language
features. Since then, no widely adopted, actively maintained tool (that I could find) focuses on
live per-line execution frequency as a first-class workflow:

- **Coverage tools** (e.g., JaCoCo) are designed around coverage (did it execute), not frequency
  (how many times did it execute)
- **Profilers** tell you where CPU time is spent
- **What's missing** is a simple way to see execution frequency under real conditions

Modern Java tooling has moved in different directions, but the idea stuck with me. I spent some
time evaluating what was available today. OpenClover's current "full support" line is Java 17,
with newer versions discussed as experimental/roadmap. JCov exists (it's an OpenJDK CodeTools
project), but the build/install path is old-school and I couldn't find a straightforward "pull a
jar from Maven Central and go" route. IntelliJ's built-in coverage is excellent for coverage, and
it stores run data as IDE coverage suites (e.g., .ic). But it's still an IDE-centric workflow—
not something you can trivially reuse in CI artifacts, feed back into vibe-coding tools/robots for
line execution count analysis, or share as a standalone, live "what ran how often" view.

At some point in that process, Claude put the choice quite bluntly:

"Do you want me to help you build JCov from source, or would you rather I create a simple custom
execution counter for you?"

That question effectively decided the direction.

## A Real-World Bug

This tool was born during a high-velocity vibe coding session where I was refactoring a core
processing engine. Standard profilers missed the following bug because the system didn't *feel*
slow yet:

I wasn't trying to optimize with a full profiler. I wanted immediate runtime visibility into what
was actually running.

**The Bug:** A `.filter(r -> r.isDuplicate())` call was being executed 19 million times in 15 seconds.  
**The Problem:** Each call was ~50 nanoseconds—easy for sampling profilers to under-sample.  
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
- Sampling profilers are fantastic, but they're statistical: you don't get exact invocation
  counts, and very short "fast but frequent" work can be easy to under-sample

**It's a "Logic X-Ray" not a "Resource Monitor."**

## How It Works

jvm-hotpath is a Java agent that instruments bytecode at class-load time using ASM. It inserts a
counter before each executable line—no sampling, no timing, just frequency.

The overhead is low enough for normal development runs. The collected data is written to an
interactive HTML report that refreshes while your application runs, showing:
- Syntax-highlighted source code
- Execution counts next to each line
- A global heatmap that makes hot paths stand out visually
- JSONP-powered polling lets you open the report directly from disk (`file://`) and watch it
  update live (no server needed)

The narrow focus is intentional: no flame graphs, no dashboards, no post-hoc traces—just
line-level execution frequency mapped directly onto source code.

The agent also writes `execution-report.json`, which gives you a machine-readable artifact you can
feed into CI steps or vibe-coding tools/robots for automated line-execution analysis.

**See it in action:**

https://github.com/user-attachments/assets/cc89451b-a41f-491e-a1f6-8e87328979c0

## Getting Started

### Maven Plugin (Recommended)

Add the plugin to your `pom.xml`:

```xml
<plugin>
    <groupId>io.github.sfkamath</groupId>
    <artifactId>jvm-hotpath-maven-plugin</artifactId>
    <version>0.2.3</version>
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

For `exec:exec`, you need a main class: pass `-Dexec.mainClass=...` or configure `mainClass`/`exec.mainClass` in `pom.xml`.

The report will be generated at `target/site/jvm-hotpath/execution-report.html`.

For multi-module projects, generated code (OpenAPI/MapStruct), or shared libraries, the plugin can
merge multiple source roots/packages in one report.
Dependency source archives can also be passed directly via `sourcepath`.

### Manual Agent Usage

If you prefer direct control:

```bash
java -javaagent:jvm-hotpath-agent.jar=packages=com.example,sourcepath=src/main/java,flushInterval=5 -jar your-app.jar
```

**Key Parameters:**
- `packages` - Comma-separated list of packages to instrument (e.g., `com.myapp`)
- `sourcepath` - Source root path(s) for code overlay (directories or source archives)
- `flushInterval` - Seconds between report refreshes (0 = no auto-flush)
- `verbose` - Print instrumentation details with clickable file URLs

### Standalone Report Regeneration

If you already have `execution-report.json` (for example from CI), you can regenerate the HTML UI
without rerunning the application:

```bash
java -jar jvm-hotpath-agent.jar --data=target/site/jvm-hotpath/execution-report.json --output=target/site/jvm-hotpath/new-report.html
```

### What This Is Not

- A coverage percentage tool (use JaCoCo for test coverage metrics)
- A CPU timing profiler (use JFR/async-profiler for duration/allocation questions)
- A 24/7 production monitoring system

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
- **Live updates** - JSONP + polling lets you open the report from disk and watch counts update
  live (no server needed)
- **Modern Java** - Tested in CI on Java 11, 17, 21, 23, and 24; Java 25 is currently blocked by
  ASM bytecode support; works with Spring Boot and Micronaut

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
**Motivation:** [Deep dive into the why](https://github.com/sfkamath/jvm-hotpath/blob/main/docs/Motivation.md)
