# Release Guide

## How Versioning Works

- CI manages versions automatically after merge to main
- Maven: `mathieudutour/github-tag-action` bumps the version tag, passed via `-Drevision=`
- Gradle: version is passed via `-PpluginVersion=` from the Maven publish job's output
- `gradle.properties` holds the local development version (`pluginVersion=X.Y.Z`)
- **Commit prefix determines version bump** (conventional commits):
  - `fix:` → patch (0.2.5 → 0.2.6)
  - `feat:` → minor (0.2.4 → 0.3.0)
  - `BREAKING CHANGE` in body → major (0.2.4 → 1.0.0)
  - Ensure the commit prefix matches the intended version bump

## Pre-Release Checklist (on the release branch)

### 1. Update versions in docs and build files

Versions in docs and build files are set manually to match the upcoming release:

- `README.md` — all version references in Quick Start and Workflow examples
- `docs/Foojay-article.md` — only update if the article is being republished
- `gradle.properties` — `pluginVersion` value
- `pom.xml` — `<revision>` default value

### 2. Update frontend dependencies

```bash
cd agent/report-ui
npm update
npm outdated  # should return empty
```

Commit the updated `package.json` and `package-lock.json`.

### 3. Verify builds

```bash
# Maven (from project root)
mvn clean install -Drevision=X.Y.Z

# Gradle plugin
cd gradle-plugin && ../gradlew build --no-daemon
```

### 4. Repository ordering in `.kts` files

All `repositories {}` blocks should prefer remote repos first, with `mavenLocal()` last:

```kotlin
// pluginManagement
gradlePluginPortal()
mavenCentral()
mavenLocal()

// dependencies
mavenCentral()
mavenLocal()
```

### 5. Review staged and unstaged changes

Before committing, review all diffs carefully:

- Ensure no stale/broken linter changes (e.g. `as String` cast syntax)
- Ensure `.gitignore` has no duplicates and ends with a newline
- Ensure Groovy DSL examples use `.set()` syntax (not `=` assignment)
- Ensure no `$buildDir` usage — use `layout.buildDirectory` instead

### 6. Merge to main

After merge, CI will:

1. Build and test across Java 11, 17, 21, 23, 24
2. Tag a new version and deploy to Maven Central
3. Publish the Gradle plugin to the Gradle Plugin Portal using the same version

## Post-Release

- Verify the artifact appears on Maven Central
- Verify the plugin appears on the Gradle Plugin Portal
- Test with a clean project (no `mavenLocal()` artifacts)
