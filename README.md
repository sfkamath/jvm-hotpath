# Execution Counter Agent

A Java agent that instruments classes at runtime to record and visualize line-level execution counts. It generates a modern, interactive HTML report with a file tree, global heatmap, and support for both dark and light modes.

## Features

- **Bytecode Instrumentation**: Automatically injects counting logic into target methods using ASM.
- **Modern UI**: Interactive report built with Vue.js 3 and PrismJS.
- **Live Updates**: Supports "serverless" real-time updates via JSONP, allowing you to watch counts increase while the app runs (even when opening the report as a local file).
- **Global Heatmap**: Consistent coloring across all source files based on the project-wide maximum execution count.
- **Activity Highlighting**: Visual "flash" indicators in the file tree when counts for a specific file increase.
- **Standalone Mode**: Regenerate the HTML report from saved JSON data without re-running the application.

## Motivation

Traditional coverage tools (Cobertura, JaCoCo, OpenClover, JCOV) either lack live reporting, struggle with modern JDKs, or hide raw counters behind opaque reports. IntelliJ and similar IDEs do not expose an easy way to visualize per-line execution as the app runs. Execution Counter bridges that gap by instrumenting production-like workloads, streaming live counts to a local HTML+JSONP UI, and surfacing changes without needing a server or sacrificing compatibility.

## Building

To build the agent JAR (shaded with all dependencies):

```bash
mvn clean package -DskipTests
```

The resulting JAR will be at `target/execution-counter-1.0.0.jar`.

> **Frontend build:** The report UI lives in `report-ui/` and is bundled via Vite. `mvn clean package` runs `frontend-maven-plugin` to execute `npm install`/`npm run build` inside that folder before packaging, producing a browser-safe `report-app.js` (IIFE bundle). When iterating on the UI you can run `npm install && npm run build` manually from `report-ui/` to refresh the bundled asset.

## Usage

Attach the agent to any Java application using the `-javaagent` flag:

```bash
java -javaagent:target/execution-counter-1.0.0.jar=packages=com.example,flushInterval=5,output=report.html,sourcepath=src/main/java -jar your-app.jar
```

### Multi-source invocation

When the instrumented application depends on multiple modules or libraries, repeat the `packages`/`sourcepath` values in a single agent argument string. Each `packages` entry should map to one of the supplied source roots (hand-written or generated) so the UI can show them under the correct project; mix generated-source directories (`target/generated-sources` etc.) with the corresponding `src/main/java` roots as needed. Package lists stay comma-separated, while source roots are joined with the platform-specific `Path.pathSeparator` (`:` on macOS/Linux, `;` on Windows). Example structure (replace the placeholders with your own paths):

```bash
java -javaagent:target/execution-counter-1.0.0.jar=packages=com.example,com.other.module,\
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

Generated classes (e.g. from annotation-processing) stay under their originating module because the `target/generated-sources` folder shares the same project root.

The agent now merges all provided source roots when overlaying code in the UI, so generated sources or auxiliary projects can be grouped under the right project name without restarting the app.

### Agent Arguments

| Argument | Description | Default |
| :--- | :--- | :--- |
| `packages` | Comma-separated list of packages to instrument (e.g., `com.myapp`). | (none) |
| `exclude` | Comma-separated list of packages/classes to explicitly skip. | (none) |
| `flushInterval` | Interval in seconds to regenerate the report while the app is running. | 0 (no auto-flush) |
| `output` | Path to the generated HTML report. | `execution-report.html` |
| `sourcepath` | Path to the root of the Java source files for code overlay. | (none) |
| `verbose` | If `true`, prints instrumentation details to stdout. | `false` |
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
java -jar target/execution-counter-1.0.0.jar --data=report.json --output=new-report.html
```

## Internal Safety Mechanisms

- **Non-Daemon Threads**: The agent starts a non-daemon "heartbeat" thread (configurable via `keepAlive`) to ensure the JVM stays alive for monitoring even if the application's main thread completes.
- **Infrastructure Exclusions**: Core libraries like `io.micronaut`, `io.netty`, and generated proxy classes are automatically excluded to prevent interference with application lifecycles.
- **Robustness**: Instrumentation is wrapped in `Throwable` blocks to prevent bytecode errors from crashing the application.
