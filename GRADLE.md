# Gradle Support for JVM Hotpath

While a native Gradle plugin is planned, you can easily use JVM Hotpath with Gradle by manually attaching the agent JAR to your `JavaExec` or `Test` tasks.

## Basic Configuration

The agent JAR is downloaded as a dependency and attached via `jvmArgs`.

### Kotlin DSL (`build.gradle.kts`)

```kotlin
val jvmHotpath by configurations.creating

dependencies {
    // Replace with the latest version
    jvmHotpath("io.github.sfkamath:jvm-hotpath-agent:0.1.0")
}

tasks.named<JavaExec>("run") {
    val agentJar = jvmHotpath.singleFile.absolutePath
    // Customize your packages and flush interval
    val agentArgs = "packages=com.example,flushInterval=5"
    jvmArgs("-javaagent:${agentJar}=${agentArgs}")
}

// Optional: Enable for tests
tasks.test {
    val agentJar = jvmHotpath.singleFile.absolutePath
    val agentArgs = "packages=com.example,output=target/test-report.html"
    jvmArgs("-javaagent:${agentJar}=${agentArgs}")
}
```

### Groovy DSL (`build.gradle`)

```groovy
configurations {
    jvmHotpath
}

dependencies {
    // Replace with the latest version
    jvmHotpath 'io.github.sfkamath:jvm-hotpath-agent:0.1.0'
}

tasks.named('run', JavaExec) {
    def agentJar = configurations.jvmHotpath.singleFile.absolutePath
    def agentArgs = "packages=com.example,flushInterval=5"
    jvmArgs "-javaagent:${agentJar}=${agentArgs}"
}

// Optional: Enable for tests
test {
    def agentJar = configurations.jvmHotpath.singleFile.absolutePath
    def agentArgs = "packages=com.example,output=target/test-report.html"
    jvmArgs "-javaagent:${agentJar}=${agentArgs}"
}
```

## Multi-Module Projects

In a multi-module project, you typically want to instrument several subprojects and provide their source paths for the interactive report.

```kotlin
tasks.named<JavaExec>("run") {
    val agentJar = jvmHotpath.singleFile.absolutePath
    
    // Instrument multiple packages
    val packages = "com.myapp.api,com.myapp.service,com.myapp.util"
    
    // Provide source paths for all modules (separated by : on Linux/macOS or ; on Windows)
    val sourcePath = listOf(
        project(":api").projectDir.resolve("src/main/java"),
        project(":service").projectDir.resolve("src/main/java"),
        project(":util").projectDir.resolve("src/main/java")
    ).joinToString(File.pathSeparator)

    jvmArgs("-javaagent:${agentJar}=packages=${packages},sourcepath=${sourcePath},flushInterval=5")
}
```

## Framework-Specific Tips

### Spring Boot

Spring Boot's `bootRun` task is a `JavaExec` task, so the configuration is identical:

```kotlin
tasks.named<org.springframework.boot.gradle.tasks.run.BootRun>("bootRun") {
    val agentJar = jvmHotpath.singleFile.absolutePath
    jvmArgs("-javaagent:${agentJar}=packages=com.example")
}
```

### Micronaut

For Micronaut, you can attach the agent to the `run` task:

```kotlin
tasks.named<JavaExec>("run") {
    val agentJar = jvmHotpath.singleFile.absolutePath
    jvmArgs("-javaagent:${agentJar}=packages=com.example")
}
```

## Configuration Parameters

The agent supports the following comma-separated parameters:

| Parameter | Description | Default |
| :--- | :--- | :--- |
| `packages` | Comma-separated list of packages to instrument (e.g., `com.example,org.myapp`). | (none) |
| `exclude` | Comma-separated list of packages/classes to explicitly skip. | (none) |
| `flushInterval` | Interval in seconds to regenerate the report while the app is running. | 0 (no auto-flush) |
| `output` | Path to the generated HTML report. | `execution-report.html` |
| `sourcepath` | Platform-specific path separator-joined roots of the Java source files. | (none) |
| `verbose` | If `true`, prints instrumentation details to stdout. | `false` |
| `keepAlive` | Keep the JVM alive via a heartbeat thread. | `true` |

## Troubleshooting

- **No hits recorded**: Ensure the `packages` argument correctly matches your application's package structure (e.g., `packages=com.example`).
- **Missing source code in report**: Verify that `sourcepath` points to the directory containing your `.java` files (e.g., `src/main/java`).
- **Dependency Resolution**: Ensure you have `mavenCentral()` in your `repositories` block.
