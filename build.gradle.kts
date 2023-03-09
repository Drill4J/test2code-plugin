@Suppress("RemoveRedundantBackticks")
plugins {
    `application`
    kotlin("jvm").apply(false)
    kotlin("multiplatform").apply(false)
    kotlin("plugin.noarg").apply(false)
    kotlin("plugin.serialization").apply(false)
    id("kotlinx-atomicfu").apply(false)
    id("com.github.johnrengelman.shadow").apply(false)
    id("com.github.hierynomus.license").apply(false)
}

group = "com.epam.drill.plugins"

repositories {
    mavenLocal()
    mavenCentral()
}
