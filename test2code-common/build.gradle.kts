plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization")
    `maven-publish`
}

val drillDsmVersion: String by extra

kotlin {
    sourceSets.commonMain {
        dependencies {
            compileOnly("org.jetbrains.kotlinx:kotlinx-serialization-core")
            api("com.epam.drill.dsm:annotations:$drillDsmVersion") { isTransitive = false }
        }
    }

    jvm()
}
