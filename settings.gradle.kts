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
    val substitutions = mapOf("kotlinx-atomicfu" to "org.jetbrains.kotlinx:atomicfu-gradle-plugin")
    resolutionStrategy.eachPlugin {
        substitutions["${requested.id}"]?.let { useModule("$it:${target.version}") }
    }
}

include(":admin-part")
include(":agent-part")
include(":common-part")
include(":tests")
include(":plugin-runner")
