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
- **Live Updates**: Supports "serverless" real-time updates via JSONP, allowing you to watch counts increase while the app runs (even when opening the report as a local file).
- **Global Heatmap**: Consistent coloring across all source files based on the project-wide maximum execution count.
- **Activity Highlighting**: Visual "flash" indicators in the file tree when counts for a specific file increase.
- **Standalone Mode**: Regenerate the HTML report from saved JSON data without re-running the application.

## Motivation

JVM Hotpath is **not a coverage tool**. Traditional coverage tools (JaCoCo, OpenClover, JCov) focus on a binary question: "*Was this line executed during tests?*". This is critical for quality metrics but useless for understanding **runtime behavior** and **hot-path analysis**.

JVM Hotpath focus on frequency: "*How many times does this line execute in a real-world workload?*"

See [docs/Motivation.md](docs/Motivation.md) for a more detailed deep-dive into the goals and architectural choices of this project.

IDEs do not expose an easy way to visualize per-line execution as the app runs. JVM Hotpath bridges that gap by instrumenting production-like workloads, streaming live frequency data to a local HTML UI, and surfacing hotspots without needing a server or sacrificing compatibility.

## Why Traditional Profilers Miss This

In the era of **vibe coding**, where large amounts of code are introduced or refactored in short bursts (often with the help of LLMs), traditional profiling workflows can feel too heavy. Attaching a commercial profiler, configuring sampling rates, and navigating complex call trees for every small logic change is a significant friction point. 

I found the need for a "low-ceremony" way to verify that new code behaves as expected. When you're moving fast, you don't always need a nanosecond-precise timing breakdown; you need an immediate, visual confirmation that your loops aren't spinning 10,000x more than they should. JVM Hotpath was built to be that lightweight "Logic X-Ray" that stays out of your way until it finds a logic error.

### The Real-World Case Study

This tool was born during a high-velocity "vibe coding" session where I was refactoring a core processing engine. With hundreds of lines changing at once, I needed to know if my architectural "vibes" matched the actual runtime reality. 

Standard profilers missed the following bug because the system didn't *feel* slow yet, but the logic was fundamentally broken:

**The Bug:** A logic check (e.g., `isValid()`) was being called 19 million times in 15 seconds.  
**The Problem:** Each call was ~50 nanoseconds - too fast for sampling profilers to notice.  
**The Impact:** Algorithmic complexity (O(N) instead of O(1)) was killing performance.

Standard profilers showed the method as "not hot" because the CPU wasn't stuck there. But 19 million calls × 50ns = 950ms of wasted time hidden in plain sight.

### How Current Tools Fall Short

| Tool Type | What It Shows | What It Misses |
|-----------|---------------|----------------|
| **Sampling Profilers**<br/>(VisualVM, JFR) | CPU-intensive methods | Fast methods called millions of times |
| **Commercial Profilers**<br/>(JProfiler, YourKit) | Timing with nanosecond precision | Usability (10x-50x overhead, heavy GUIs) |
| **APM Tools**<br/>(Datadog, New Relic) | Request/span-level metrics | Line-level logic errors |

### The Key Insight: Frequency ≠ Duration

Java profilers focus on **where the CPU is hot** (timing).  
This tool shows **how many times code runs** (frequency).

In modern Java:
- JIT compilation makes methods fast
- The bottleneck is often algorithmic (O(N) vs O(1))
- Logic errors create millions of unnecessary calls
- Each call is too fast to show up in sampling

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

- **Java:** 11 or higher (verified on 11, 17, 21, 23; supports 24)
- **Build Tool:** Maven 3.6+ or Gradle 7.0+

The agent is compiled to Java 11 bytecode for maximum compatibility. Support for Java 25 is currently a hard limit (see Development section).

## Building

To build the agent JAR (shaded with all dependencies):

```bash
mvn clean package -DskipTests
```

The resulting JAR will be at `target/jvm-hotpath-agent-0.1.0.jar`.

> **Frontend build:** The report UI lives in `report-ui/` and is bundled via Vite. `mvn clean package` runs `frontend-maven-plugin` to execute `npm install`/`npm run build` inside that folder before packaging, producing a browser-safe `report-app.js` (IIFE bundle). When iterating on the UI you can run `npm install && npm run build` manually from `report-ui/` to refresh the bundled asset.

## Quick Start

### Maven

The easiest way to use JVM Hotpath is via the Maven plugin. It automatically finds the agent, configures your test runner (Surefire/Failsafe), and detects your project structure.

Add this to your `pom.xml`:

```xml
<plugin>
    <groupId>io.github.sfkamath</groupId>
    <artifactId>jvm-hotpath-maven-plugin</artifactId>
    <!-- Use the latest version from the Maven Central badge at the top of this file -->
    <version>0.1.1</version>
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
```

Then run your tests:
```bash
mvn verify
```

> **Note:** For multi-module projects or to run the provided integration tests, use the `it` profile: `mvn verify -Pit`.

### Gradle

See [GRADLE.md](GRADLE.md) for Gradle configuration.

## Usage

### Maven Plugin (Recommended)

The easiest way to use JVM Hotpath is via the Maven plugin. It automatically finds the agent, configures your test runner (Surefire/Failsafe), and detects your project structure.

Add this to your `pom.xml`:

```xml
<plugin>
    <groupId>io.github.sfkamath</groupId>
    <artifactId>jvm-hotpath-maven-plugin</artifactId>
    <!-- Use the latest version from the Maven Central badge at the top of this file -->
    <version>0.1.1</version>
    <executions>
        <execution>
            <goals>
                <goal>prepare-agent</goal>
            </goals>
        </execution>
    </executions>
    <configuration>
        <!-- Optional: Auto-flush report every 5 seconds -->
        <flushInterval>5</flushInterval>
    </configuration>
</plugin>
```

Then run your tests:
```bash
mvn verify
```
The report will be generated at `target/site/execution-report.html`.

#### Multi-source invocation

Just like the manual agent, the plugin can handle multiple source roots and packages. This is ideal for projects with **generated resources** (like OpenAPI or MapStruct) or when you want to instrument multiple modules in one report.

**Via `pom.xml`:**

```xml
<configuration>
    <!-- Your project's groupId and main sources are auto-detected. -->
    <!-- Add extra packages to instrument (comma-separated): -->
    <packages>com.example,com.other.module</packages>
    
    <!-- Add extra source roots (joined with the platform separator : or ;): -->
    <sourcepath>
        module-a/src/main/java:module-a/target/generated-sources:module-b/src/main/java
    </sourcepath>
</configuration>
```

**Via Command Line:**

You can override or append configuration via system properties. **Note:** CLI properties are only respected if the corresponding parameter is *not* explicitly defined in the `<configuration>` block of your `pom.xml`.

```bash
mvn verify \
  -Djvm-hotpath.packages=com.example,com.other.module \
  -Djvm-hotpath.sourcepath=module-a/src/main/java:module-a/target/generated-sources:module-b/src/main/java
```

*Note: Use `:` (macOS/Linux) or `;` (Windows) as the path separator.*

#### Running the Application

To run your application with the agent active, ensure the `${argLine}` property is passed to your JVM runner.

**Using a Profile (Recommended):**
Configure `exec-maven-plugin` to use the `${argLine}` populated by the agent.

```xml
<profile>
    <id>instrument</id>
    <build>
        <plugins>
            <plugin>
                <groupId>io.github.sfkamath</groupId>
                <artifactId>jvm-hotpath-maven-plugin</artifactId>
                <!-- Use the latest version from the Maven Central badge at the top of this file -->
                <version>0.1.1</version>
                <executions>
                    <execution>
                        <goals><goal>prepare-agent</goal></goals>
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
                    <!-- Automatically picks up the agent from ${argLine} and your main class -->
                    <commandlineArgs>${argLine} -classpath %classpath ${exec.mainClass}</commandlineArgs>
                </configuration>
            </plugin>
        </plugins>
    </build>
</profile>
```

Then run (ensure the `instrument` profile is active for prefix resolution):
```bash
mvn jvm-hotpath:prepare-agent exec:exec -Pinstrument -Dexec.mainClass="com.example.Main"
```

### Advanced Configuration

The plugin uses **"Smart Defaults"** but allows additive configuration.

| Config | Description | Default Behavior |
| :--- | :--- | :--- |
| `packages` | Packages to instrument. | **Appends** to project's `groupId`. |
| `sourcepath` | Source roots for the report. | **Appends** to project's `src/main/java`. |
| `includes` | External dependencies to resolve. | Resolves `sources.jar` for given artifacts. |

#### Example: Including External Dependencies

If you want to instrument code from a dependency (and see its source code in the report), configure `includes` in your `pom.xml` to automatically resolve the source JAR from Maven:

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

**Via Command Line:**
You can achieve the same by pointing `sourcepath` directly to a sources JAR in your local repository:

```bash
mvn verify \
  -Djvm-hotpath.packages=com.example.shared \
  -Djvm-hotpath.sourcepath=$HOME/.m2/repository/com/example/shared-library/1.0.0/shared-library-1.0.0-sources.jar
```

### Manual Agent Usage

If you prefer not to use the plugin, you can attach the agent manually.

**Build the agent:**
```bash
mvn clean package -DskipTests
```
The JAR will be located at `agent/target/jvm-hotpath-agent-0.1.0.jar`.

**Run with Agent:**

```bash
java -javaagent:${PATH_TO_AGENT_JAR}=packages=com.example,sourcepath=src/main/java,flushInterval=5,output=target/site/execution-report.html -jar your-app.jar
```

#### Multi-source invocation

When the instrumented application depends on multiple modules or libraries, repeat the `packages`/`sourcepath` values in a single agent argument string. Each `packages` entry should map to one of the supplied source roots (hand-written or generated) so the UI can show them under the correct project; mix generated-source directories (`target/generated-sources`) etc. with the corresponding `src/main/java` roots as needed. Package lists stay comma-separated, while source roots are joined with the platform-specific `Path.pathSeparator` (`:` on macOS/Linux, `;` on Windows). Example structure:

```bash
java -javaagent:${PATH_TO_AGENT_JAR}=packages=com.example,com.other.module,\ 
    flushInterval=5,\ 
    output=target/site/execution-report.html,\ 
    sourcepath=module-a/src/main/java:module-a/target/generated-sources:module-b/src/main/java,\ 
    -jar your-app.jar
```

The agent merges every source root into the tree. Visually it looks like:

```
module-a
  com.example
  com.example.generated
module-b
  com.other.module
```

#### Agent Arguments

| Argument | Description | Default |
| :--- | :--- | :--- |
| `packages` | Comma-separated list of packages to instrument (e.g., `com.myapp`). | (none) |
| `exclude` | Comma-separated list of packages/classes to explicitly skip. | (none) |
| `flushInterval` | Interval in seconds to regenerate the report while the app is running. | 0 (no auto-flush) |
| `output` | Path to the generated HTML report. | `target/site/execution-report.html` |
| `sourcepath` | Path to the root of the Java source files for code overlay. | (none) |
| `verbose` | If `true`, prints instrumentation details and flush success messages (with clickable file URLs) to stdout. | `false` |
| `keepAlive` | Keep the JVM alive via a heartbeat thread (useful for scheduled apps without a server). | `true` |

## Viewing the Report

1.  Open the generated `target/site/execution-report.html` file in any modern web browser.
2.  If `flushInterval` is set, the report will automatically poll for updates from a sibling `execution-report.js` file.
3.  **No Web Server Required**: Thanks to the JSONP implementation, live updates work even when the file is opened directly from disk (`file://` protocol).
4.  If you open the report from disk and nothing renders, hard-refresh once (the `report-app.js` bundle is copied alongside the report and may be cached).

## Report Artifacts

The agent produces both human-readable and machine-readable output in the `target/site/` directory:

### Primary Outputs
- **`execution-report.html`**: The interactive web UI for developers. Self-contained with the initial data snapshot.
- **`execution-report.json`**: Pure JSON data for machine consumption (CI pipelines, LLM analysis, etc.).

### Supporting Assets
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

## Standalone Report Generation

If you have a saved `execution-report.json` file and want to regenerate the HTML UI (e.g., after updating the template or changing themes):

```bash
java -jar ${PATH_TO_AGENT_JAR} --data=target/site/execution-report.json --output=target/site/new-report.html
```

## Development

- **Development JDK:** Java 21
- **Bytecode Target:** Java 11 (for maximum runtime compatibility)
- **Instrumentation Engine:** ASM 9.9.1 (supports up to Java 24 bytecode)
- **CI Testing Matrix:** Covers Java 11, 17, 21, 23 and 24.

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
