import java.util.Properties

plugins {
    java
    id("org.springframework.boot") version "3.5.10"
    id("io.spring.dependency-management") version "1.1.7"
    id("io.github.sfkamath.jvm-hotpath")
}

val rootProps = Properties()
rootProps.load(file("../gradle.properties").inputStream())

group = "io.github.sfkamath"
version = (rootProps["pluginVersion"] as? String)
    ?: error("pluginVersion not set in gradle.properties")

repositories {
    mavenCentral()
    mavenLocal()
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

jvmHotpath {
    packages.set("io.github.sfkamath.jvmhotpath.sample")
    flushInterval.set(0)
}

tasks.test {
    useJUnitPlatform()
}
