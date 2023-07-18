import java.net.URI
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import com.hierynomus.gradle.license.tasks.LicenseCheck
import com.hierynomus.gradle.license.tasks.LicenseFormat
import kotlinx.benchmark.gradle.JvmBenchmarkTarget

plugins {
    kotlin("jvm")
    kotlin("plugin.allopen")
    kotlin("plugin.noarg")
    kotlin("plugin.serialization")
    id("kotlinx-atomicfu")
    id("org.jetbrains.kotlinx.benchmark")
    id("com.github.hierynomus.license")
}

group = "com.epam.drill.plugins.test2code"
version = rootProject.version

val kotlinxCollectionsVersion: String by parent!!.extra
val kotlinxSerializationVersion: String by parent!!.extra
val kotlinxBenchmarkVersion: String by parent!!.extra
val ktorVersion: String by parent!!.extra
val kodeinVersion: String by parent!!.extra
val bcelVersion: String by parent!!.extra
val javassistVersion: String by parent!!.extra
val jacocoVersion: String by parent!!.extra
val atomicfuVersion: String by parent!!.extra
val drillAdminVersion: String by parent!!.extra

repositories {
    mavenLocal()
    mavenCentral()
}

configurations.all {
    resolutionStrategy.cacheDynamicVersionsFor(5, TimeUnit.MINUTES)
}

dependencies {
    implementation(kotlin("stdlib-jdk8"))
    implementation("org.jetbrains.kotlinx:kotlinx-benchmark-runtime:$kotlinxBenchmarkVersion")

    testCompileOnly(project(":test2code-api"))
    testCompileOnly(project(":test2code-common"))

    testImplementation(kotlin("test-junit5"))
    testImplementation("org.jetbrains.kotlinx:kotlinx-collections-immutable:$kotlinxCollectionsVersion")
    testImplementation("org.jetbrains.kotlinx:kotlinx-serialization-json:$kotlinxSerializationVersion")
    testImplementation("org.kodein.di:kodein-di-jvm:$kodeinVersion")
    testImplementation("org.apache.bcel:bcel:$bcelVersion")
    testImplementation("org.javassist:javassist:$javassistVersion")
    testImplementation("org.jacoco:org.jacoco.core:$jacocoVersion")
    testImplementation("org.jetbrains.kotlinx:atomicfu:$atomicfuVersion")
    testImplementation("org.junit.jupiter:junit-jupiter:5.5.2")
    testImplementation("io.kotlintest:kotlintest-runner-junit5:3.3.2")
    testImplementation("io.mockk:mockk:1.9.3")
    testImplementation("com.epam.drill:test-framework:$drillAdminVersion@jar") { isChanging = true }
    testImplementation("com.epam.drill:admin-core:$drillAdminVersion:all@jar") { isChanging = true }
    testImplementation(project(":common"))
    testImplementation(project(":ktor-swagger"))
    testImplementation(project(":plugin-api-admin"))
    testImplementation(project(":plugin-api-agent"))
    testImplementation(project(":test2code-admin"))
    testImplementation(project(":test2code-agent"))
    testImplementation(project(":tests"))
}

kotlin.sourceSets.all {
    languageSettings.optIn("kotlin.Experimental")
    languageSettings.optIn("kotlin.time.ExperimentalTime")
    languageSettings.optIn("kotlinx.serialization.ExperimentalSerializationApi")
}

tasks {
    test {
        useJUnitPlatform()
        systemProperty("plugin.feature.drealtime", false)
    }
    clean {
        delete(".kotlintest")
    }
    withType<KotlinCompile> {
        kotlinOptions.jvmTarget = "1.8"
    }
}

benchmark {
    configurations.getByName("main") {
        iterationTime = 5
        iterationTimeUnit = "ms"
    }
    targets.register("test") {
        (this as JvmBenchmarkTarget).jmhVersion = "1.21"
    }
}

allOpen {
    annotation("org.openjdk.jmh.annotations.State")
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
