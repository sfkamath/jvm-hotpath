import java.util.Properties

plugins {
    java
    id("io.micronaut.application") version "4.5.4"
}

val rootProps = Properties()
rootProps.load(file("../gradle.properties").inputStream())

group = "io.github.sfkamath"
version = rootProps["pluginVersion"] as String

repositories {
    mavenCentral()
    mavenLocal()
}

micronaut {
    version = "4.5.4"
    runtime("netty")
}

dependencies {
    implementation(platform("io.micronaut.platform:micronaut-platform:4.10.9"))

    implementation("io.github.sfkamath:jvm-hotpath-agent:${version}")
    implementation("io.micronaut:micronaut-http-server-netty")
    implementation("io.micronaut:micronaut-jackson-databind")
    implementation("io.micronaut:micronaut-inject")
    annotationProcessor("io.micronaut:micronaut-inject-java:4.10.9")
    testImplementation("io.micronaut:micronaut-http-client")
    testImplementation("io.micronaut.test:micronaut-test-junit5:4.10.3")
    testImplementation("org.junit.jupiter:junit-jupiter-api")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testAnnotationProcessor("io.micronaut:micronaut-inject-java:4.10.9")
    implementation("jakarta.annotation:jakarta.annotation-api:2.1.1")
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

// Apply JVM Hotpath agent manually after project evaluation
project.afterEvaluate {
    val agentConfig = configurations.create("jvmHotpathAgent")
    dependencies.add(agentConfig.name, "io.github.sfkamath:jvm-hotpath-agent:${version}")

    val agentJar = agentConfig.files.first { it.name.startsWith("jvm-hotpath-agent") }
    val agentArgs = "packages=io.github.sfkamath.jvmhotpath.sample.micronaut,output=${layout.buildDirectory.get()}/site/jvm-hotpath/execution-report.html"

    tasks.withType<Test> {
        jvmArgs("-javaagent:${agentJar}=${agentArgs}")
    }

    tasks.withType<JavaExec> {
        jvmArgs("-javaagent:${agentJar}=${agentArgs}")
    }
}

tasks.test {
    useJUnitPlatform()
}
