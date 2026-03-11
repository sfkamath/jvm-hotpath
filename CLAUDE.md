# Claude Code Instructions

## Release Process

Follow [RELEASE-GUIDE.md](RELEASE-GUIDE.md) for all release-related work.

## Build Commands

- Maven: `mvn clean install -Drevision=X.Y.Z`
- Gradle plugin: `cd gradle-plugin && ../gradlew build --no-daemon`
- Frontend: `cd agent/report-ui && npm update`

## Key Conventions

- Versions in docs/build files are set manually to match the upcoming CI release
- `gradle.properties` is the single source of truth for the local dev version
- Repository order: remote repos first (`gradlePluginPortal`, `mavenCentral`), `mavenLocal()` last
- Groovy DSL must use `.set()` for `Property<T>` types, not `=` assignment
- `instrumentTests` defaults to `false` in both Maven and Gradle plugins
- Never use deprecated `$buildDir` — use `layout.buildDirectory`
