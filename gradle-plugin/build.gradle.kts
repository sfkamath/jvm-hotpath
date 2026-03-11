import java.util.Properties

plugins {
    `java-gradle-plugin`
    `maven-publish`
    id("com.gradle.plugin-publish") version "1.3.1"
}

val rootProps = Properties()
rootProps.load(file("../gradle.properties").inputStream())

group = "io.github.sfkamath"
val pluginVersion = (findProperty("pluginVersion") as String?) ?: (rootProps["pluginVersion"] as String)
version = pluginVersion

repositories {
    mavenCentral()
    mavenLocal()
}

dependencies {
    implementation("io.github.sfkamath:jvm-hotpath-agent:${version}")
}

gradlePlugin {
    website = "https://github.com/sfkamath/jvm-hotpath"
    vcsUrl = "https://github.com/sfkamath/jvm-hotpath"

    plugins {
        create("jvmHotpath") {
            id = "io.github.sfkamath.jvm-hotpath"
            implementationClass = "io.github.sfkamath.jvmhotpath.gradle.JvmHotpathPlugin"
            displayName = "JVM Hotpath"
            description = "Real-time line-level execution frequency analysis for JVM"
            tags = listOf("execution-counts", "hotpath", "runtime-analysis", "jvm", "asm", "bytecode", "java-agent")
        }
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}

val generatePluginProperties by tasks.registering {
    val outputDir = layout.buildDirectory.dir("generated/resources/plugin-properties")
    outputs.dir(outputDir)
    doLast {
        val dir = outputDir.get().asFile
        dir.mkdirs()
        dir.resolve("jvm-hotpath-plugin.properties").writeText("version=${project.version}\n")
    }
}

sourceSets.main {
    resources.srcDir(generatePluginProperties)
}
