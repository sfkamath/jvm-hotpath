# JVM Hotpath TODO Log
Status: ⚠️ in progress  
Date: 2026-01-30

- ✅ [🔥 high priority] Verify the agent works when multiple libraries/components are on the classpath (registry + icy-reader), ensuring both source trees are included.
  - ⚙️ Run the agent against the registry app with `packages=radio.registry,com.sfk.radio` and `sourcepath` pointing to `station-registry/src/main/java`, `station-registry/target/generated-sources/openapi/src/main/java`, and `icy-reader/src/main/java`. ✅ counts emitted for both projects after manual trigger.
  - ⚙️ Confirm the JSON report contains files from the OpenAPI outputs (e.g., `radio/registry/api/model/IdentityStatus.java`) and `icy-reader` (e.g., `com/sfk/radio/scrape/IcyStreamReader.java`). ✅ Report now includes generated files and com/sfk/radio entries.
- ✅ Remove the `java-bom` parent so `jvm-hotpath` is self-contained, pinning dependency/plugin versions in `pom.xml`.
- ⬜ Replace `System.out` logging with `java.util.logging`, keeping instrumentation logs configurable via agent args (`verbose=true`).
- ⬜ Fix JSONP live refresh by switching to `fetch()` with pure JSON (see `JSONP-LIVE-REFRESH-ISSUE.md`).
    - Update `ReportGenerator.java` to write pure JSON instead of JSONP wrapper.
    - Update `report-template.html` to poll via `fetch()` instead of script injection.
    - Fix `updateTreeData()` to always update counts (not just on increase).
  - ⚙️ Introduce project-root grouping in the tree data so files hang beneath their project name before collapsing `packages`.
    - ✅ Report now invites a project segment before `com/sfk/radio` or `radio/registry` entries.
- ⬜ Verify hardened agent fixes Micronaut shutdown issue with updated exclusions and `Throwable` catch.
- ⬜ Build Micronaut and Spring Boot test fixtures that run the agent, emit reports, and validate via Playwright.
    - Keep fixtures minimal (single endpoint or scheduled task) for fast CI.
    - Add Maven profiles (`-Pmicronaut-tests`, `-Pspring-tests`).
    - Playwright scripts validate UI renders and counts refresh.
- ⬜ Configure CI with Java LTS matrix (17/21/23) running fixtures + Playwright checks.
- ⬜ Extract hardcoded class exclusions from `ExecutionCountTransformer` into external config file.
- ✅ Create Maven plugin for easier agent integration.
- ⬜ Create Gradle plugin for easier agent integration.
- ⬜ Publish to Maven Central:
    - Open Sonatype OSSRH ticket for `groupId` (e.g., `io.github.yourorg`).
    - Add GPG signing, source/javadoc jars, and `distributionManagement`.
    - Automate deploy/release in CI with stored credentials.
- 📝 README already highlights the gap this fills vs Cobertura/JaCoCo/JCov ✅

## Completed
- ✅ Modernized report UI with Vite bundle, JSONP cache-busting, offline status.
- ✅ Added detailed README explaining motivation and ecosystem gap.
- ✅ Documented JSONP issue and fetch() solution.
- ✅ Hardened agent with proper Micronaut exclusions and Throwable catching.

## Pre-release housekeeping
- ⬜ Choose a neutral public name/groupId for the plugin (avoid internal repo names) and update Maven coordinates/docs accordingly.
- ⬜ Squash local commits into a clean public-friendly history before the first Maven Central release.
