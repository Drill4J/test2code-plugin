plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization")
}

kotlin {
    sourceSets.commonMain {
        dependencies {
            api(project(":common"))

            //provided by drill runtime or clients
            compileOnly("com.epam.drill:common")
            compileOnly("org.jetbrains.kotlinx:kotlinx-serialization-runtime-common")
        }
    }

    targets {
        jvm {
            val main by compilations
            main.defaultSourceSet {
                dependencies {
                    //provided by drill runtime or clients
                    compileOnly("io.ktor:ktor-locations") { isTransitive = false }
                    compileOnly("org.jetbrains.kotlinx:kotlinx-serialization-runtime")
                }
            }
        }
    }
}
