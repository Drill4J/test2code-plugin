import java.net.URI
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import com.hierynomus.gradle.license.tasks.LicenseCheck
import com.hierynomus.gradle.license.tasks.LicenseFormat

@Suppress("RemoveRedundantBackticks")
plugins {
    `application`
    kotlin("jvm")
    kotlin("plugin.noarg")
    id("com.github.hierynomus.license")
    id("com.github.johnrengelman.shadow")
}

group = "com.epam.drill.plugins.test2code"

val ajaltCliktVersion: String by parent!!.extra

repositories {
    mavenLocal()
    mavenCentral()
}

@Suppress("HasPlatformType")
val jarDependencies by configurations.creating {
    attributes.attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage.JAVA_API))
}

dependencies {
    implementation(kotlin("stdlib"))
    implementation("com.github.ajalt:clikt:$ajaltCliktVersion")
    implementation(project(":test2code-admin"))
    implementation(project(":test2code-api"))

    testImplementation(kotlin("test"))
    testImplementation(kotlin("test-junit"))
}

kotlin.sourceSets.all {
    languageSettings.optIn("kotlin.Experimental")
    languageSettings.optIn("kotlin.time.ExperimentalTime")
    languageSettings.optIn("kotlinx.serialization.ExperimentalSerializationApi")
}

val jarMainClassName = "com.epam.drill.plugins.test2code.cli.CliKt"

tasks {
    withType<KotlinCompile> {
        kotlinOptions.jvmTarget = "1.8"
    }
    shadowJar {
        isZip64 = true
        manifest.attributes["Main-Class"] = jarMainClassName
        configurations = listOf(jarDependencies)
        archiveBaseName.set("test2code-cli.jar")
        destinationDirectory.set(file("$buildDir/shadowLibs"))
    }
}

noArg {
    annotation("kotlinx.serialization.Serializable")
}

@Suppress("DEPRECATION")
application {
    mainClassName = jarMainClassName
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
