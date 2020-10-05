plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization")
    `maven-publish`
}

kotlin {
    sourceSets.commonMain {
        dependencies {
            compileOnly("org.jetbrains.kotlinx:kotlinx-serialization-runtime-common")
        }
    }

    jvm {
        val main by compilations
        main.defaultSourceSet {
            dependencies {
                compileOnly("org.jetbrains.kotlinx:kotlinx-serialization-runtime")
            }
        }
    }
}
