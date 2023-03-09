import java.net.URI
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import com.hierynomus.gradle.license.tasks.LicenseCheck
import com.hierynomus.gradle.license.tasks.LicenseFormat

plugins {
    kotlin("multiplatform")
    kotlin("plugin.noarg")
    kotlin("plugin.serialization")
    id("com.github.hierynomus.license")
}

group = "com.epam.drill.plugins"

val kotlinxSerializationVersion: String by parent!!.extra

repositories {
    mavenLocal()
    mavenCentral()
}

kotlin {
    targets {
        jvm()
    }
    @Suppress("UNUSED_VARIABLE")
    sourceSets {
        val commonMain by getting {
            dependencies {
                compileOnly("org.jetbrains.kotlinx:kotlinx-serialization-core:$kotlinxSerializationVersion")
                api(project(":dsm-annotations")) { isTransitive = false }
            }
        }
    }
}

tasks {
    withType<KotlinCompile> {
        kotlinOptions.jvmTarget = "1.8"
        kotlinOptions.freeCompilerArgs += "-Xuse-experimental=kotlin.Experimental"
        kotlinOptions.freeCompilerArgs += "-Xuse-experimental=kotlin.time.ExperimentalTime"
        kotlinOptions.freeCompilerArgs += "-Xuse-experimental=kotlinx.serialization.ExperimentalSerializationApi"
    }
}

noArg {
    annotation("kotlinx.serialization.Serializable")
}

@Suppress("UNUSED_VARIABLE")
license {
    headerURI = URI("https://raw.githubusercontent.com/Drill4J/drill4j/develop/COPYRIGHT")
    val licenseFormatSources by tasks.registering(LicenseFormat::class) {
        source = fileTree("$projectDir/src").also {
            include("**/*.kt", "**/*.java", "**/*.groovy")
        }
    }
    val licenseCheckSources by tasks.registering(LicenseCheck::class) {
        source = fileTree("$projectDir/src").also {
            include("**/*.kt", "**/*.java", "**/*.groovy")
        }
    }
}
