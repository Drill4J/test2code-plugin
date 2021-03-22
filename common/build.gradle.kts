plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization")
}

kotlin.sourceSets.all {
    languageSettings.useExperimentalAnnotation("kotlinx.serialization.ExperimentalSerializationApi")
    languageSettings.useExperimentalAnnotation("kotlinx.serialization.InternalSerializationApi")
}

kotlin {
    sourceSets.commonMain {
        dependencies {
            //provided by drill runtime or clients
            compileOnly("org.jetbrains.kotlinx:kotlinx-serialization-core")
        }
    }
    jvm {
        val main by compilations
        main.defaultSourceSet {
            dependencies {
                //provided by drill runtime or clients
                compileOnly("org.jetbrains.kotlinx:kotlinx-serialization-core")
            }
        }
    }

}

