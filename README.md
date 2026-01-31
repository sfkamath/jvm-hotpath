# JVM Hotpath Agent

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

JVM Hotpath is **not a coverage tool**. Traditional coverage tools (JaCoCo, OpenClover, JCov) focus on a binary question: *"Was this line executed during tests?"*. This is critical for quality metrics but useless for understanding **runtime behavior** and **hot-path analysis**.

JVM Hotpath focus on frequency: *"How many times does this line execute in a real-world workload?"*. 

IDEs do not expose an easy way to visualize per-line execution as the app runs. JVM Hotpath bridges that gap by instrumenting production-like workloads, streaming live frequency data to a local HTML UI, and surfacing hotspots without needing a server or sacrificing compatibility.

See [docs/Motivation.md](docs/Motivation.md) for a more detailed deep-dive into the goals and architectural choices of this project.

## Building

To build the agent JAR (shaded with all dependencies):

```bash
mvn clean package -DskipTests
```

The resulting JAR will be at `target/jvm-hotpath-agent-0.1.0.jar`.

> **Frontend build:** The report UI lives in `report-ui/` and is bundled via Vite. `mvn clean package` runs `frontend-maven-plugin` to execute `npm install`/`npm run build` inside that folder before packaging, producing a browser-safe `report-app.js` (IIFE bundle). When iterating on the UI you can run `npm install && npm run build` manually from `report-ui/` to refresh the bundled asset.

## Usage

### Maven Plugin (Recommended)

The easiest way to use JVM Hotpath is via the Maven plugin. It automatically finds the agent, configures your test runner (Surefire/Failsafe), and detects your project structure.

Add this to your `pom.xml`:

```xml
<plugin>
    <groupId>io.github.sfkamath</groupId>
    <artifactId>jvm-hotpath-maven-plugin</artifactId>
    <version>0.1.0</version>
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
The report will be generated at `target/execution-report.html`.

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
                <version>0.1.0</version>
                <executions>
                    <execution>
                        <goals><goal>prepare-agent</goal></goals>
                    </execution>
                </executions>
                <configuration>
                    <flushInterval>5</flushInterval>
                    <packages>com.example,com.other.module</packages>
                    <sourcepath>
                        module-a/src/main/java:module-a/target/generated-sources:module-b/src/main/java
                    </sourcepath>
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

Then run:
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
java -javaagent:agent/target/jvm-hotpath-agent-0.1.0.jar=packages=com.example,com.other.module,flushInterval=5,output=report.html,sourcepath=module-a/src/main/java:module-b/src/main/java -jar your-app.jar
```

#### Multi-source invocation

When the instrumented application depends on multiple modules or libraries, repeat the `packages`/`sourcepath` values in a single agent argument string. Each `packages` entry should map to one of the supplied source roots (hand-written or generated) so the UI can show them under the correct project; mix generated-source directories (`target/generated-sources` etc.) with the corresponding `src/main/java` roots as needed. Package lists stay comma-separated, while source roots are joined with the platform-specific `Path.pathSeparator` (`:` on macOS/Linux, `;` on Windows). Example structure (replace the placeholders with your own paths):

```bash
java -javaagent:agent/target/jvm-hotpath-agent-0.1.0.jar=packages=com.example,com.other.module,\
    flushInterval=5,\
    output=reports/execution-report.html,\
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
| `output` | Path to the generated HTML report. | `execution-report.html` |
| `sourcepath` | Path to the root of the Java source files for code overlay. | (none) |
| `verbose` | If `true`, prints instrumentation details and flush success messages (with clickable file URLs) to stdout. | `false` |
| `keepAlive` | Keep the JVM alive via a heartbeat thread (useful for scheduled apps without a server). | `true` |

## Viewing the Report

1.  Open the generated `execution-report.html` file in any modern web browser.
2.  If `flushInterval` is set, the report will automatically poll for updates from a sibling `execution-report.js` file.
3.  **No Web Server Required**: Thanks to the JSONP implementation, live updates work even when the file is opened directly from disk (`file://` protocol).
4.  If you open the report from disk and nothing renders, hard-refresh once (the `report-app.js` bundle is copied alongside the report and may be cached).

## Report Artifacts

For an output path like `report.html`, the agent writes three sibling files:

- `report.html` (self-contained UI + initial snapshot)
- `report.json` (latest snapshot payload)
- `report.js` (JSONP wrapper for live updates)
- `report-app.js` (bundled UI runtime copied alongside the report)

The JSON/JSONP payload format is:

```json
{
  "generatedAt": 1700000000000,
  "files": [
    { "path": "com/example/Foo.java", "counts": { "12": 3 }, "content": "..." }
  ]
}
```

See `docs/jsonp-live-updates.md` for implementation details and gotchas.

## Standalone Report Generation

If you have a saved `execution-report.json` file and want to regenerate the HTML UI (e.g., after updating the template or changing themes):

```bash
java -jar agent/target/jvm-hotpath-agent-0.1.0.jar --data=report.json --output=new-report.html
```

## Internal Safety Mechanisms

- **Non-Daemon Threads**: The agent starts a non-daemon "heartbeat" thread (configurable via `keepAlive`) to ensure the JVM stays alive for monitoring even if the application's main thread completes.
- **Infrastructure Exclusions**: Core libraries like `io.micronaut`, `io.netty`, and generated proxy classes are automatically excluded to prevent interference with application lifecycles.
- **Robustness**: Instrumentation is wrapped in `Throwable` blocks to prevent bytecode errors from crashing the application.
