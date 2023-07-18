import java.net.URI
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import com.hierynomus.gradle.license.tasks.LicenseCheck
import com.hierynomus.gradle.license.tasks.LicenseFormat

plugins {
    kotlin("jvm")
    kotlin("plugin.noarg")
    kotlin("plugin.serialization")
    id("kotlinx-atomicfu")
    id("com.github.hierynomus.license")
}

group = "com.epam.drill.plugins.test2code"
version = rootProject.version

val kotlinxCollectionsVersion: String by parent!!.extra
val kotlinxSerializationVersion: String by parent!!.extra
val ktorVersion: String by parent!!.extra
val bcelVersion: String by parent!!.extra
val kodeinVersion: String by parent!!.extra
val jacocoVersion: String by parent!!.extra
val atomicfuVersion: String by parent!!.extra
val flywaydbVersion: String by parent!!.extra
val drillAdminVersion: String by parent!!.extra
val testsSkipIntegrationTests: String by parent!!.extra

repositories {
    mavenLocal()
    mavenCentral()
}

sourceSets {
    create("build1")
    create("build2")
}

@Suppress("HasPlatformType")
val testData by configurations.creating

configurations {
    all { resolutionStrategy.cacheDynamicVersionsFor(5, TimeUnit.MINUTES) }
    testImplementation.get().extendsFrom(testData)
    getByName("build1Implementation").extendsFrom(testData)
    getByName("build2Implementation").extendsFrom(testData)
}

@Suppress("UnusedReceiverParameter")
fun DependencyHandler.ktor(module: String, version: String = ktorVersion): String = "io.ktor:ktor-$module:$version"

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-collections-immutable:$kotlinxCollectionsVersion")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:$kotlinxSerializationVersion")
    implementation("org.jetbrains.kotlinx:atomicfu:$atomicfuVersion")
    implementation("org.apache.bcel:bcel:$bcelVersion")
    implementation("org.kodein.di:kodein-di-jvm:$kodeinVersion")
    implementation("org.jacoco:org.jacoco.core:$jacocoVersion")
    implementation("org.flywaydb:flyway-core:$flywaydbVersion")
    implementation(ktor("auth"))
    implementation(ktor("auth-jwt"))
    implementation(ktor("client-cio"))
    implementation(ktor("locations"))
    implementation(ktor("serialization"))
    implementation(ktor("server-core"))
    implementation(ktor("server-netty"))
    implementation(ktor("server-test-host"))
    implementation(ktor("websockets"))
    implementation(project(":common"))
    implementation(project(":plugin-api-agent"))
    implementation(project(":plugin-api-admin"))
    implementation(project(":dsm-test-framework"))
    implementation(project(":jacoco"))
    implementation(project(":test2code-api"))
    implementation(project(":test2code-common"))
    implementation(project(":test2code-admin"))
    implementation(project(":test2code-agent"))
    implementation(project(":ktor-swagger"))
    implementation("com.epam.drill:test-framework:$drillAdminVersion@jar") { isChanging = true }
    implementation("com.epam.drill:admin-core:$drillAdminVersion:all@jar") { isChanging = true }

    testImplementation(kotlin("test-junit5"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.5.2")
    testImplementation("io.kotlintest:kotlintest-runner-junit5:3.3.2")
    testImplementation("io.mockk:mockk:1.9.3")

    testData(project(":test-data"))
}

kotlin.sourceSets.all {
    languageSettings.optIn("kotlin.Experimental")
    languageSettings.optIn("kotlin.time.ExperimentalTime")
    languageSettings.optIn("kotlinx.serialization.ExperimentalSerializationApi")
}

tasks {
    val prepareDistr by registering(Sync::class) {
        from(rootProject.tasks["testDistZip"])
        into("distr/adminStorage")
    }
    val integrationTest by registering(Test::class) {
        description = "Runs the integration tests"
        group = "verification"
        enabled = !testsSkipIntegrationTests.toBoolean()
        useJUnitPlatform()
        systemProperty("plugin.config.path", rootDir.resolve("plugin_config.json"))
        systemProperty("drill.plugins.test2code.features.realtime", false)
        systemProperty("analytic.disable", true)
        dependsOn("build1Classes")
        dependsOn("build2Classes")
        dependsOn(prepareDistr)
        mustRunAfter(test)
    }
    check.get().dependsOn(integrationTest)
    clean {
        delete(".kotlintest")
        delete("distr")
        delete("work")
    }
    withType<KotlinCompile> {
        kotlinOptions.jvmTarget = "1.8"
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
