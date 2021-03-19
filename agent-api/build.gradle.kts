plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization")
    `maven-publish`
}

kotlin {
    sourceSets.commonMain {
        dependencies {
            compileOnly(project(":common"))
            compileOnly("org.jetbrains.kotlinx:kotlinx-serialization-core")
        }
    }

    jvm()
}
