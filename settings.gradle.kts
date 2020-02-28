rootProject.name = "test2code-plugin"


pluginManagement {
    val kotlinVersion: String by settings
    val atomicFuVersion: String by settings
    val drillGradlePluginVersion: String by settings
    plugins {
        kotlin("jvm") version kotlinVersion
        kotlin("plugin.serialization") version kotlinVersion
        id("kotlinx-atomicfu") version atomicFuVersion
        id("com.epam.drill.version.plugin") version drillGradlePluginVersion
        id("com.github.johnrengelman.shadow") version "5.2.0"
    }
    repositories {
        gradlePluginPortal()
        maven(url = "https://oss.jfrog.org/artifactory/list/oss-release-local")
        maven(url = "https://dl.bintray.com/kotlin/kotlinx/")
    }
    resolutionStrategy.eachPlugin {
        when ("${requested.id}") {
            "kotlinx-atomicfu" -> "org.jetbrains.kotlinx:atomicfu-gradle-plugin:${target.version}"
            else -> null
        }?.let { useModule(it) }
    }
}

include(":admin-part")
include(":agent-part")
include(":common-part")
include(":tests")

includeBuild("plugin-runner")
