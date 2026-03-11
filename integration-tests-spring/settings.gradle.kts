pluginManagement {
    includeBuild("../gradle-plugin")
    repositories {
        gradlePluginPortal()
        mavenCentral()
        mavenLocal()
    }
}

rootProject.name = "jvm-hotpath-integration-tests-spring"
