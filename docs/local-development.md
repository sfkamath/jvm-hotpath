# Local Development Guide

## Publishing locally for testing

The project has two separate build systems: Maven (for the agent, Maven plugin, and integration tests) and Gradle (for the Gradle plugin). Testing a local build requires both to be published to `~/.m2`.

### Step 1 — Install the agent to mavenLocal

The Gradle plugin depends on the agent JAR, so install it first:

```bash
mvn install -pl agent -am -DskipTests -Drevision=0.2.8-SNAPSHOT
```

The `-Drevision` flag overrides the version at the command line without editing `pom.xml`. Use a `-SNAPSHOT` suffix to disambiguate local builds from released versions.

### Step 2 — Publish the Gradle plugin to mavenLocal

```bash
cd gradle-plugin && ../gradlew publishToMavenLocal --no-daemon -PpluginVersion=0.2.8-SNAPSHOT
```

`-PpluginVersion` overrides the version in `gradle.properties` without editing that file.

This publishes two artifacts (see [What is the marker artifact?](#what-is-the-marker-artifact) below):

- `io.github.sfkamath:jvm-hotpath-gradle-plugin:X.Y.Z` — the plugin JAR
- `io.github.sfkamath.jvm-hotpath:io.github.sfkamath.jvm-hotpath.gradle.plugin:X.Y.Z` — the marker POM

### Verify both are present

```bash
ls ~/.m2/repository/io/github/sfkamath/jvm-hotpath-gradle-plugin/X.Y.Z/
ls ~/.m2/repository/io/github/sfkamath/jvm-hotpath/io.github.sfkamath.jvm-hotpath.gradle.plugin/X.Y.Z/
```

### Step 3 — Install the full Maven build (optional)

To also install the Maven plugin, integration tests, and other modules:

```bash
mvn install -DskipTests
```

---

## What is the marker artifact?

When a Gradle build applies a plugin by ID — e.g. `id("io.github.sfkamath.jvm-hotpath")` — Gradle needs to resolve that string to a real JAR. It does this by looking for a **marker POM** at a well-known Maven coordinate derived from the plugin ID:

```
<pluginId>:<pluginId>.gradle.plugin:<version>
```

For this project:

```
io.github.sfkamath.jvm-hotpath:io.github.sfkamath.jvm-hotpath.gradle.plugin:0.2.8
```

That POM contains nothing except a single `<dependency>` pointing at the real implementation JAR (`io.github.sfkamath:jvm-hotpath-gradle-plugin:0.2.8`). Gradle follows that pointer to download the actual plugin code.

The marker is automatically generated and published by Gradle's `java-gradle-plugin` plugin. It is **only** written to the repository when you run a publish task (`publishToMavenLocal`, `publishPlugins`, etc.) — running `build` or `jar` alone does not create it.

If the marker is missing, applying the plugin fails with:

```
Plugin [id: 'io.github.sfkamath.jvm-hotpath', version: '0.2.8'] was not found
```

even though the plugin JAR is present in `~/.m2`. This is the most common local testing pitfall.
