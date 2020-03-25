plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization")
}

kotlin {
    sourceSets.commonMain {
        dependencies {
            compileOnly("com.epam.drill:common")
            compileOnly("org.jetbrains.kotlinx:kotlinx-serialization-runtime-common")
        }
    }

    targets {
        jvm {
            val main by compilations
            main.defaultSourceSet {
                dependencies {
                    compileOnly("org.jetbrains.kotlinx:kotlinx-serialization-runtime")
                }
            }
        }
    }
}
