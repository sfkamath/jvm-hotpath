plugins {
    id("com.github.ben-manes.versions") version "0.53.0"
}

// Root build.gradle.kts for JVM Hotpath
// This provides convenience tasks for running Gradle integration tests
// and dependency version management.

// Note: The gradle-plugin is resolved via includeBuild() in settings.gradle.kts
// This means the plugin is built from source and doesn't need to be published
// for local development and integration testing.
