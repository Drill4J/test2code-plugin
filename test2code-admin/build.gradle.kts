import java.net.URI
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import com.hierynomus.gradle.license.tasks.LicenseCheck
import com.hierynomus.gradle.license.tasks.LicenseFormat

plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
    kotlin("plugin.noarg")
    id("kotlinx-atomicfu")
    id("com.github.johnrengelman.shadow")
    id("com.github.hierynomus.license")
}

group = "com.epam.drill.plugins"

val kotlinxCoroutinesVersion: String by parent!!.extra
val kotlinxSerializationVersion: String by parent!!.extra
val kotlinxCollectionsVersion: String by parent!!.extra
val bcelVersion: String by parent!!.extra
val jacocoVersion: String by parent!!.extra
val atomicfuVersion: String by parent!!.extra
val lubenZstdVersion: String by parent!!.extra
val microutilsLoggingVersion: String by parent!!.extra

repositories {
    mavenLocal()
    mavenCentral()
}

@Suppress("HasPlatformType")
val jarDependencies by configurations.creating {
    attributes.attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage.JAVA_API))
}
configurations.implementation.get().extendsFrom(jarDependencies)

kotlin.sourceSets.all {
    languageSettings.optIn("kotlinx.coroutines.ExperimentalCoroutinesApi")
    languageSettings.optIn("kotlinx.serialization.ExperimentalSerializationApi")
}

dependencies {
    jarDependencies("org.apache.bcel:bcel:$bcelVersion")
    jarDependencies("org.jacoco:org.jacoco.core:$jacocoVersion")
    jarDependencies(project(":test2code-api"))
    jarDependencies(project(":test2code-common"))
    jarDependencies(project(":jacoco"))

    compileOnly("org.jetbrains.kotlinx:atomicfu:$atomicfuVersion")
    compileOnly("com.github.luben:zstd-jni:$lubenZstdVersion")

    implementation(kotlin("stdlib-jdk8"))
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:$kotlinxSerializationVersion")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-protobuf:$kotlinxSerializationVersion")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:$kotlinxCoroutinesVersion")
    implementation("org.jetbrains.kotlinx:kotlinx-collections-immutable:$kotlinxCollectionsVersion")
    implementation("io.github.microutils:kotlin-logging-jvm:$microutilsLoggingVersion")
    implementation(project(":plugin-api-admin"))

    api(project(":dsm"))
    api(project(":dsm-annotations"))
    api(project(":admin-analytics"))

    testImplementation(kotlin("test-junit5"))
    testImplementation("org.jetbrains.kotlinx:atomicfu:$atomicfuVersion")
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("ch.qos.logback:logback-classic:1.2.3")
    testImplementation(project(":dsm-test-framework"))
}


tasks {
    test {
        useJUnitPlatform()
    }
    withType<KotlinCompile> {
        kotlinOptions.jvmTarget = "1.8"
        kotlinOptions.freeCompilerArgs += "-Xuse-experimental=kotlin.Experimental"
        kotlinOptions.freeCompilerArgs += "-Xuse-experimental=kotlin.time.ExperimentalTime"
        kotlinOptions.freeCompilerArgs += "-Xuse-experimental=kotlinx.serialization.ExperimentalSerializationApi"
    }
    shadowJar {
        isZip64 = true
        configurations = listOf(jarDependencies)
        archiveFileName.set("test2code-admin.jar")
        destinationDirectory.set(file("$buildDir/shadowLibs"))
        dependencies {
            exclude("/META-INF/**", "/*.class", "/*.html")
        }
        relocate("org.jacoco", "$group.test2code.shadow.org.jacoco")
        relocate("org.objectweb", "$group.test2code.shadow.org.objectweb")
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
