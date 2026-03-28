# JVM Hotpath TODO Log
Status: ⚠️ in progress  
Date: 2026-02-01

## 🔴 Critical (Must Have)
- ✅ **append mode**: drift detection was broken — `rehydrate()` ran before any checksums were recorded, so mismatched sources were always seeded. Fixed by passing `sourcePath` into `rehydrate()` and computing current checksums directly from source files at rehydration time.
- ✅ **Self-contained build**: Shaded agent and independent parent POM.
- ✅ **Fix live refresh**: Switched from JSONP to `fetch()` with pure JSON for HTTP loads.
- ✅ **Standardized Logging**: Replaced `System.out` with `java.util.logging`.
- ✅ **Framework Stability**: Verified Micronaut/Netty doesn't crash during instrumentation.
- ✅ **Silent instrumentation gaps**: Classes loaded via `ClassWriter.COMPUTE_FRAMES` → `getCommonSuperClass()` → `Class.forName()` were silently skipped due to JVM re-entrancy protection. Fixed by overriding `getCommonSuperClass` to read class bytes via `ClassLoader.getResourceAsStream()` instead of loading classes.
- ✅ **Maven Central publishing setup**:
    - ✅ Verify io.github.sfkamath on Central Portal.
    - ✅ Configure GPG signing plugin in `pom.xml` (via `ossrh` profile).
    - ✅ Add `maven-javadoc-plugin` and `maven-source-plugin` (via `ossrh` profile).
    - ✅ Add required POM metadata (name, description, url, licenses, developers, scm).
    - ✅ Add `central-publishing-maven-plugin` for automated deployment.
    - ✅ Configure GitHub Actions for GPG signing and Central Portal deployment.
- ✅ **Basic CI**: GitHub Actions workflow running on Java 21.
- ✅ **Clean git history**: Squash/rebase into a professional public-friendly history.
- ✅ **LICENSE file**: Add MIT License to the project root.
- ✅ **GRADLE.md**: Comprehensive usage guide for Gradle users.

## 🚀 Go Live Checklist (Final Release Activities)

- ✅ **Enable Auto-Publish**: Add `<autoPublish>true</autoPublish>` and `<waitUntil>validated</waitUntil>` to `pom.xml`.
- ✅ **Public Visibility**: Change repository visibility to Public.
- ✅ **Maven Central Badge**: Add the `io.github.sfkamath` central badge to `README.md` once first release is live.
- ✅ **Badge Maintenance**: Clean up and standardize all `README.md` badges (Java CI, Version, License).

## 🟡 Important (Should Have)

- ✅ **Java LTS matrix CI**: Verified builds/tests on Java 11, 17, 21, and 23.
- ✅ **Working test fixtures**: Isolated integration tests for both Spring Boot and Micronaut.
- ⬜ **External exclusions config**: Move hardcoded exclusions from `ExecutionCountTransformer` to a `.properties` or `.json` file.
- ✅ **Project-aware reporting**: Group source files by project/module in the UI tree.

## 🟢 Nice to Have (Can Wait)

- ⬜ **Native Gradle plugin**: Automate configuration for Gradle projects.
- ⬜ **Playwright tests**: UI-level verification of report rendering and live updates.
- ✅ **Multi-source verification**: Confirmed agent handles multiple source roots (generated + manual) correctly.

---

## 📘 Maven Central Onboarding Details

### 1. Account & Namespace Verification
- **Primary Path (Recommended)**: Login to [central.sonatype.com](https://central.sonatype.com/) using GitHub OAuth. Verify the `io.github.sfkamath` namespace via the automated GitHub verification tool.
- **Legacy Path**: Create a ticket at [issues.sonatype.org](https://issues.sonatype.org/) (Project: OSSRH). 
    - Create a temporary GitHub repo named after the ticket ID (e.g., `OSSRH-12345`) to prove ownership.

### 2. POM Requirements for Central
The following plugins must be configured in the parent `pom.xml` before the first release:
- `maven-source-plugin`: Attach source JARs.
- `maven-javadoc-plugin`: Attach Javadoc JARs.
- `maven-gpg-plugin`: Sign artifacts (requires a GPG key).
- `central-publishing-maven-plugin` (or `nexus-staging-maven-plugin` for legacy).

### 3. CI/CD Secrets
Ensure the following are added to GitHub Secrets for the `publish` job:
- `MAVEN_GPG_PASSPHRASE`
- `MAVEN_GPG_PRIVATE_KEY` (The ASCII armored private key)
- `SONATYPE_USERNAME` / `SONATYPE_PASSWORD` (or Portal Token)

---

## 📝 Completed Milestones
- ✅ **UI Overhaul**: Condensed 12px design, IntelliJ icons, and `localStorage` persistence.
- ✅ **Bytecode Hardening**: Atomic counter initialization and stable class attribution.
- ✅ **Data Integrity**: Fixed "late-loading" bug that caused count loss for dynamic proxies.
- ✅ **Maven Plugin**: Released `jvm-hotpath-maven-plugin` for "smart default" configuration.
- ✅ **Readme Documentation**: Detailed motivation and "Logic X-Ray" vs "CPU Thermometer" analysis.
