rootProject.name = "test2code-plugin"

pluginManagement {
    val kotlinVersion: String by extra
    val kotlinxBenchmarkVersion: String by extra
    val licenseVersion: String by extra
    val atomicfuVersion: String by extra
    val shadowPluginVersion: String by extra
    plugins {
        kotlin("jvm") version kotlinVersion
        kotlin("multiplatform") version kotlinVersion
        kotlin("plugin.allopen") version kotlinVersion
        kotlin("plugin.noarg") version kotlinVersion
        kotlin("plugin.serialization") version kotlinVersion
        id("kotlinx-atomicfu") version atomicfuVersion
        id("org.jetbrains.kotlinx.benchmark") version kotlinxBenchmarkVersion
        id("com.github.hierynomus.license") version licenseVersion
        id("com.github.johnrengelman.shadow") version shadowPluginVersion
    }
    repositories {
        mavenLocal()
        mavenCentral()
        gradlePluginPortal()
    }
    resolutionStrategy.eachPlugin {
        if(requested.id.id == "kotlinx-atomicfu") useModule("org.jetbrains.kotlinx:atomicfu-gradle-plugin:${target.version}")
    }
}

val includeSharedLib: Settings.(String) -> Unit = {
    include(it)
    project(":$it").projectDir = file("lib-jvm-shared/$it")
}

includeSharedLib("dsm")
includeSharedLib("dsm-annotations")
includeSharedLib("dsm-test-framework")
includeSharedLib("kt2dts")
includeSharedLib("kt2dts-cli")
includeSharedLib("kt2dts-api-sample")
includeSharedLib("admin-analytics")
includeSharedLib("common")
includeSharedLib("logger-api")
includeSharedLib("ktor-swagger")
includeSharedLib("plugin-api-admin")
includeSharedLib("plugin-api-agent")
includeSharedLib("test-data")
include("jacoco")
include("test2code-common")
include("test2code-api")
include("test2code-admin")
include("test2code-cli")
include("test2code-agent")
include("tests")
include("load-tests")
include("benchmarks")
