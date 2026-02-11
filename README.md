<p align="center">
  <img src="agent/src/main/resources/io/github/sfkamath/jvmhotpath/favicon.png" width="200" alt="JVM Hotpath Logo">
</p>

# JVM Hotpath Agent

[![Java CI](https://github.com/sfkamath/jvm-hotpath/actions/workflows/ci.yml/badge.svg)](https://github.com/sfkamath/jvm-hotpath/actions/workflows/ci.yml)
[![Maven Central](https://img.shields.io/maven-central/v/io.github.sfkamath/jvm-hotpath-agent.svg?label=Maven%20Central)](https://search.maven.org/search?q=g:%22io.github.sfkamath%22%20AND%20a:%22jvm-hotpath-agent%22)
[![Version](https://img.shields.io/github/v/tag/sfkamath/jvm-hotpath)](https://github.com/sfkamath/jvm-hotpath/tags)
[![License](https://img.shields.io/github/license/sfkamath/jvm-hotpath)](LICENSE)

A Java agent that instruments classes at runtime to record and visualize line-level execution counts. It generates a modern, interactive HTML report with a file tree, global heatmap, and support for both dark and light modes.


## Features

- **Bytecode Instrumentation**: Automatically injects counting logic into target methods using ASM.
- **Frequency Analysis**: Tracks exactly how many times each line executes, rather than just "if" it was hit.
- **Modern UI**: Interactive report built with Vue.js 3 and PrismJS.
- **Live Updates**: Uses JSONP + polling for "serverless" real-time updates, so you can watch counts increase while the app runs (even from `file://`).
- **Global Heatmap**: Consistent coloring across all source files based on the project-wide maximum execution count.
- **Activity Highlighting**: Visual "flash" indicators in the file tree when counts for a specific file increase.
- **Standalone Mode**: Regenerate the HTML report from saved JSON data without re-running the application.

## Motivation

JVM Hotpath is **not a coverage tool**. Coverage tools (e.g., JaCoCo, OpenClover, JCov) are designed around coverage (did it execute), not frequency (how many times did it execute). That's critical for quality metrics, but limited for understanding **runtime behavior** and **hot-path analysis**.

JVM Hotpath focuses on frequency: "*How many times does this line execute in a real-world workload?*"

See [docs/Motivation.md](docs/Motivation.md) for a more detailed deep-dive into the goals and architectural choices of this project.

IDEs do not expose an easy way to visualize per-line execution as the app runs. JVM Hotpath bridges that gap by instrumenting production-like workloads, streaming live frequency data to a local HTML UI, and surfacing hotspots without needing a server or sacrificing compatibility.

## Why Traditional Profilers Miss This

In the era of **vibe coding**, where large amounts of code are introduced or refactored in short bursts (often with the help of LLMs), traditional profiling workflows can feel too heavy. Attaching a commercial profiler, configuring sampling rates, and navigating complex call trees for every small logic change is a significant friction point. 

I found the need for a "low-ceremony" way to verify that new code behaves as expected. When you're moving fast, you don't always need a nanosecond-precise timing breakdown; you need an immediate, visual confirmation that your loops aren't spinning 10,000x more than they should. JVM Hotpath was built to be that lightweight "Logic X-Ray" that stays out of your way until it finds a logic error.

### The Real-World Case Study

This tool was born during a high-velocity "vibe coding" session where I was refactoring a core processing engine. With hundreds of lines changing at once, I needed to know if my architectural "vibes" matched the actual runtime reality. 

Standard profilers missed the following bug because the system didn't *feel* slow yet, but the logic was fundamentally broken:

**The Bug:** A logic check (e.g., `isValid()`) was being called 19 million times in 15 seconds.  
**The Problem:** Each call was ~50 nanoseconds - easy for sampling profilers to under-sample.  
**The Impact:** Algorithmic complexity (O(N) instead of O(1)) was killing performance.

Standard profilers showed the method as "not hot" because the CPU wasn't stuck there. But 19 million calls × 50ns = 950ms of wasted time hidden in plain sight.

### How Current Tools Fall Short

| Tool Type | What It Shows | What It Misses |
|-----------|---------------|----------------|
| **Sampling Profilers**<br/>(VisualVM, JFR) | CPU-intensive methods | Fast methods called millions of times |
| **Commercial Profilers**<br/>(JProfiler, YourKit) | Deep timing and call tracing | Always-on convenience (heavier workflow, and instrumentation/tracing can add noticeable overhead) |
| **APM Tools**<br/>(Datadog, New Relic) | Request/span-level metrics | Line-level logic errors |

### The Key Insight: Frequency ≠ Duration

Java profilers focus on **where the CPU is hot** (timing).  
This tool shows **how many times code runs** (frequency).

In modern Java:
- JIT compilation makes methods fast
- The bottleneck is often algorithmic (O(N) vs O(1))
- Logic errors create millions of unnecessary calls
- Sampling profilers are statistical: they do not provide exact invocation counts, and very short "fast but frequent" work can be under-sampled

**Example:**
```
Sampler says: "Line 96 uses 2.3% CPU time"
Hotpath says: "Line 96: executed 19,147,293 times"
```

One is a performance metric. The other is a logic error screaming at you.

### What This Tool Does Differently

✅ **Zero timing overhead** - Just counts, no nanosecond measurements  
✅ **Counts every execution** - No sampling, no missing fast methods  
✅ **Simple output** - JSON/HTML, not a heavy GUI  
✅ **LLM-friendly** - Pipe the report to Claude/GPT for analysis  
✅ **Logic-focused** - Finds algorithmic problems, not just CPU hotspots  

**It's a "Logic X-Ray" not a "CPU Thermometer".**

![JVM Hotpath Gson Demo](https://github.com/user-attachments/assets/cc89451b-a41f-491e-a1f6-8e87328979c0)

When you see "Line 42: executed 19 million times" in a 15-second run, you don't need to measure nanoseconds. You need to fix your algorithm.

## Requirements

- **Java:** 11 or higher (tested in CI on 11, 17, 21, 23, and 24)
- **Build Tool:** Maven 3.6+ or Gradle 7.0+

The agent is compiled to Java 11 bytecode for maximum compatibility. Java 25 is currently blocked by upstream bytecode-tooling support (see Development section).

## Quick Start

### Maven Plugin (Recommended)

Add this `instrument` profile to your `pom.xml`:

```xml
<profile>
    <id>instrument</id>
    <build>
        <plugins>
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
                    <flushInterval>5</flushInterval>
                </configuration>
            </plugin>
            <plugin>
                <groupId>org.codehaus.mojo</groupId>
                <artifactId>exec-maven-plugin</artifactId>
                <version>3.5.0</version>
                <configuration>
                    <executable>java</executable>
                    <commandlineArgs>${argLine} -classpath %classpath ${exec.mainClass}</commandlineArgs>
                </configuration>
            </plugin>
        </plugins>
    </build>
</profile>
```

Run your app:

```bash
mvn -Pinstrument jvm-hotpath:prepare-agent exec:exec
```

> **Note:** `exec:exec` requires a main class. Provide it via `-Dexec.mainClass=...`, or configure `mainClass`/`exec.mainClass` in your `pom.xml`.

Report output: `target/site/jvm-hotpath/execution-report.html`

### Direct `-javaagent` (Alternative)

Run the app with `-javaagent` to instrument without the Maven plugin. For more details, see [Manual `-javaagent` Workflow](#manual-javaagent-workflow).

## Workflows

Choose either the Maven plugin workflow (recommended) or the manual `-javaagent` workflow.
Gradle: see [GRADLE.md](GRADLE.md).

### Maven Plugin Workflow

Use the `instrument` profile from Quick Start.
This workflow requires `exec:exec` to have a main class (via `-Dexec.mainClass=...` or config).

For `exec:exec`, `prepare-agent` resolves `exec.mainClass` in this order:
1. `-Dexec.mainClass`
2. `jvm-hotpath.mainClass`
3. `main.class`
4. `mainClass`
5. `start-class`
6. `spring-boot.run.main-class`

If no main class can be resolved, `prepare-agent` fails fast. This validation is skipped for non-`exec` runs (for example test-only runs).

Common Maven plugin extensions:

#### Add More Packages or Source Roots

Use this when your run spans multiple modules or generated sources:

```xml
<configuration>
    <packages>com.example,com.other.module</packages>
    <sourcepath>
        module-a/src/main/java:module-a/target/generated-sources:module-b/src/main/java
    </sourcepath>
</configuration>
```

Or as a one-off CLI override:

```bash
mvn -Pinstrument jvm-hotpath:prepare-agent exec:exec \
  -Djvm-hotpath.packages=com.example,com.other.module \
  -Djvm-hotpath.sourcepath=module-a/src/main/java:module-a/target/generated-sources:module-b/src/main/java
```

Use `:` on macOS/Linux and `;` on Windows for `sourcepath`.

#### Include Dependency Sources

Use `<includes>` in `pom.xml` for repeatable team/project config:

```xml
<configuration>
    <packages>com.legacy.utils</packages>
    <includes>
        <include>
            <groupId>com.example</groupId>
            <artifactId>shared-library</artifactId>
            <packageName>com.example.shared</packageName>
        </include>
    </includes>
</configuration>
```

Use CLI override only for one-off local runs:

```bash
mvn -Pinstrument jvm-hotpath:prepare-agent exec:exec \
  -Djvm-hotpath.packages=com.example.shared \
  -Djvm-hotpath.sourcepath=$HOME/.m2/repository/com/example/shared-library/1.0.0/shared-library-1.0.0-sources.jar
```

`sourcepath` accepts directories and source archives (`.jar`/`.zip`). If you provide archives manually, match source and runtime versions.

<a id="manual-javaagent-workflow"></a>
### Manual `-javaagent` Workflow

Download the agent from Maven Central:

```bash
wget https://repo1.maven.org/maven2/io/github/sfkamath/jvm-hotpath-agent/0.2.3/jvm-hotpath-agent-0.2.3.jar
export PATH_TO_AGENT_JAR="$PWD/jvm-hotpath-agent-0.2.3.jar"
```

Or build locally:

```bash
mvn clean package -DskipTests
export PATH_TO_AGENT_JAR="$PWD/agent/target/jvm-hotpath-agent-0.2.3.jar"
```

Run with single-source config:

```bash
java -javaagent:${PATH_TO_AGENT_JAR}=packages=com.example,sourcepath=src/main/java,flushInterval=5,output=target/site/jvm-hotpath/execution-report.html -jar your-app.jar
```

Report output: `target/site/jvm-hotpath/execution-report.html`

Run with multi-source config:

```bash
AGENT_ARGS="packages=com.example,com.other.module,flushInterval=5,output=target/site/jvm-hotpath/execution-report.html,sourcepath=module-a/src/main/java:module-a/target/generated-sources:module-b/src/main/java"
java -javaagent:${PATH_TO_AGENT_JAR}="${AGENT_ARGS}" -jar your-app.jar
```

Use `:` on macOS/Linux and `;` on Windows for `sourcepath`.

### Configuration Options

Smart defaults (plugin workflow):

- `packages`: starts with your project `groupId`
- `sourcepath`: starts with compile source roots (typically `src/main/java`)
- `output`: defaults to `target/site/jvm-hotpath/execution-report.html`
- `flushInterval`: defaults to `0` (set to `5` for live updates)

Use the table below as the full reference.

| Option | Scope | Agent Arg | Maven Plugin Config | Notes |
| :--- | :--- | :--- | :--- | :--- |
| `packages` | Agent + Plugin | `packages=` | `jvm-hotpath.packages` / `<packages>` | Plugin seeds with project `groupId`, then appends configured values. |
| `exclude` | Agent + Plugin | `exclude=` | `jvm-hotpath.exclude` / `<exclude>` | Exclusion list passed through to agent. |
| `flushInterval` | Agent + Plugin | `flushInterval=` | `jvm-hotpath.flushInterval` / `<flushInterval>` | Interval in seconds. Default `0` (no periodic flush). |
| `output` | Agent + Plugin | `output=` | `jvm-hotpath.output` / `<output>` | Default `target/site/jvm-hotpath/execution-report.html`. |
| `sourcepath` | Agent + Plugin | `sourcepath=` | `jvm-hotpath.sourcepath` / `<sourcepath>` | Supports directories and source archives (`.jar`/`.zip`). |
| `verbose` | Agent + Plugin | `verbose=` | `jvm-hotpath.verbose` / `<verbose>` | Extra instrumentation/flush logging. |
| `keepAlive` | Agent + Plugin | `keepAlive=` | `jvm-hotpath.keepAlive` / `<keepAlive>` | Agent default is `true`; plugin emits when enabled. |
| `mainClass` | Plugin only | n/a | `jvm-hotpath.mainClass` / `<mainClass>` | Populates `exec.mainClass` for `exec:exec`. |
| `includes` | Plugin only | n/a | `<includes>` | Resolves dependency sources and appends them to `sourcepath`. |
| `propertyName` | Plugin only | n/a | `jvm-hotpath.propertyName` / `<propertyName>` | Target property for injected `-javaagent` string (default `argLine`). |
| `skip` | Plugin only | n/a | `jvm-hotpath.skip` / `<skip>` | Skips plugin execution. |

## Report and Output

### Viewing the Report

1.  Open the generated `target/site/jvm-hotpath/execution-report.html` file in any modern web browser.
2.  If `flushInterval` is set, the report will automatically poll for updates from a sibling `execution-report.js` file.
3.  **No Web Server Required**: Thanks to the JSONP implementation, live updates work even when the file is opened directly from disk (`file://` protocol).
4.  If you open the report from disk and nothing renders, hard-refresh once (the `report-app.js` bundle is copied alongside the report and may be cached).

### Report Artifacts

The agent produces both human-readable and machine-readable output in the `target/site/jvm-hotpath/` directory:

#### Primary Outputs
- **`execution-report.html`**: The interactive web UI for developers. Self-contained with the initial data snapshot.
- **`execution-report.json`**: Pure JSON data for machine consumption (CI pipelines, LLM analysis, etc.).

#### Supporting Assets
- **`execution-report.js`**: A JSONP wrapper used by the HTML report for live updates without a web server.
- **`report-app.js`**: The bundled Vue.js runtime used by the HTML UI.

The JSON payload format is optimized for clarity:

```json
{
  "generatedAt": 1700000000000,
  "files": [
    {
      "path": "com/example/Foo.java", 
      "project": "my-module",
      "counts": { "12": 3, "13": 47293 }, 
      "content": "..." 
    }
  ]
}
```

See `docs/jsonp-live-updates.md` for implementation details and gotchas.

### Standalone Report Generation

If you have a saved `execution-report.json` file and want to regenerate the HTML UI (e.g., after updating the template or changing themes):

```bash
java -jar ${PATH_TO_AGENT_JAR} --data=target/site/jvm-hotpath/execution-report.json --output=target/site/jvm-hotpath/new-report.html
```

## Development

- **Development JDK:** Java 21
- **Bytecode Target:** Java 11 (for maximum runtime compatibility)
- **Instrumentation Engine:** ASM 9.9.1 (supports up to Java 24 bytecode)
- **CI Testing Matrix:** Covers Java 11, 17, 21, 23 and 24.

To build the agent JAR (shaded with all dependencies):

```bash
mvn clean package -DskipTests
```

The resulting JAR will be at `agent/target/jvm-hotpath-agent-0.2.3.jar`.

> **Frontend build:** The report UI lives in `report-ui/` and is bundled via Vite. `mvn clean package` runs `frontend-maven-plugin` to execute `npm install`/`npm run build` inside that folder before packaging, producing a browser-safe `report-app.js` (IIFE bundle). When iterating on the UI you can run `npm install && npm run build` manually from `report-ui/` to refresh the bundled asset.

> **Java 25 Note:** Support for Java 25 is currently blocked until the ASM project releases a version that supports the finalized Java 25 bytecode specification. Using the agent on a Java 25 JVM will likely result in an `UnsupportedClassVersionError` during instrumentation.

## Internal Safety Mechanisms

- **Non-Daemon Threads**: The agent starts a non-daemon "heartbeat" thread (configurable via `keepAlive`) to ensure the JVM stays alive for monitoring even if the application's main thread completes.
- **Infrastructure Exclusions**: Core libraries like `io.micronaut`, `io.netty`, and generated proxy classes are automatically excluded to prevent interference with application lifecycles.
- **Robustness**: Instrumentation is wrapped in `Throwable` blocks to prevent bytecode errors from crashing the application.

## Contributing

We built this because we needed it. If you need it too, let's make it better together.

- 🐛 Found a bug? [Open an issue](https://github.com/sfkamath/jvm-hotpath/issues)
- 💡 Have an idea? [Start a discussion](https://github.com/sfkamath/jvm-hotpath/discussions)
- 🔧 Want to contribute? [Submit a PR](https://github.com/sfkamath/jvm-hotpath/pulls)

## License

[MIT License](LICENSE) - Free to use, modify, and distribute.
