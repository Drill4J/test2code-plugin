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

group = "com.epam.drill.plugins.test2code"

val kotlinxSerializationVersion: String by parent!!.extra
val ktorVersion: String by parent!!.extra

repositories {
    mavenLocal()
    mavenCentral()
}

kotlin {
    targets {
        jvm()
        linuxX64()
        mingwX64()
        macosX64()
    }
    @Suppress("UNUSED_VARIABLE")
    sourceSets {
        all {
            languageSettings.optIn("kotlin.Experimental")
            languageSettings.optIn("kotlin.time.ExperimentalTime")
            languageSettings.optIn("kotlinx.serialization.ExperimentalSerializationApi")
        }
        val commonMain by getting {
            dependencies {
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-core:$kotlinxSerializationVersion")
            }
        }
        val jvmMain by getting {
            dependencies {
                implementation("io.ktor:ktor-locations:$ktorVersion") { isTransitive = false }
            }
        }
    }
}

tasks {
    withType<KotlinCompile> {
        kotlinOptions.jvmTarget = "1.8"
    }
    val jvmMain = kotlin.jvm().compilations["main"]
    val generateTsd by registering(JavaExec::class) {
        val argumentProvider = CommandLineArgumentProvider {
            val generatePaths = jvmMain.output.classesDirs + jvmMain.runtimeDependencyFiles.files + jvmMain.compileDependencyFiles.files
            val tsdDir = buildDir.resolve("ts").apply { mkdirs() }
            val tsdFile = tsdDir.resolve("test2code.d.ts")
            mutableListOf(
                "--module=@drill4j/test2code-types",
                "--output=${tsdFile.path}",
                "--cp=${generatePaths.joinToString(",")}"
            )
        }
        group = "kt2dts"
        classpath = project(":kt2dts-cli").tasks["fatJar"].outputs.files
        argumentProviders += argumentProvider
    }
    generateTsd.get().dependsOn(jvmMain.compileAllTaskName)
    generateTsd.get().dependsOn(project(":kt2dts-cli").tasks["fatJar"])
    compileKotlinMetadata.get().dependsOn(generateTsd)
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
