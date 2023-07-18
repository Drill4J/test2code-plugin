import java.net.URI
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import com.hierynomus.gradle.license.tasks.LicenseCheck
import com.hierynomus.gradle.license.tasks.LicenseFormat

plugins {
    kotlin("jvm")
    kotlin("plugin.noarg")
    kotlin("plugin.serialization")
    id("com.github.hierynomus.license")
}

group = "com.epam.drill.plugins.test2code"
version = rootProject.version

val kotlinxCollectionsVersion: String by parent!!.extra
val kotlinxSerializationVersion: String by parent!!.extra
val ktorVersion: String by parent!!.extra
val kodeinVersion: String by parent!!.extra
val bcelVersion: String by parent!!.extra
val javassistVersion: String by parent!!.extra
val jacocoVersion: String by parent!!.extra
val drillAdminVersion: String by parent!!.extra

repositories {
    mavenLocal()
    mavenCentral()
}

sourceSets.create("customBuild")

@Suppress("HasPlatformType")
val testData by configurations.creating

configurations {
    all { resolutionStrategy.cacheDynamicVersionsFor(5, TimeUnit.MINUTES) }
    testImplementation.get().extendsFrom(testData)
    getByName("customBuildImplementation").extendsFrom(testData)
}

@Suppress("UnusedReceiverParameter")
fun DependencyHandler.ktor(module: String, version: String = ktorVersion): String = "io.ktor:ktor-$module:$version"

dependencies {
    testCompileOnly(project(":test2code-common"))
    testCompileOnly(project(":test2code-api"))
    testCompileOnly(project(":test2code-agent"))

    testImplementation(kotlin("test-junit5"))
    testImplementation("org.jetbrains.kotlinx:kotlinx-collections-immutable:$kotlinxCollectionsVersion")
    testImplementation("org.jetbrains.kotlinx:kotlinx-serialization-json:$kotlinxSerializationVersion")
    testImplementation(ktor("server-test-host"))
    testImplementation(ktor("auth"))
    testImplementation(ktor("auth-jwt"))
    testImplementation(ktor("server-netty"))
    testImplementation(ktor("locations"))
    testImplementation(ktor("server-core"))
    testImplementation(ktor("websockets"))
    testImplementation(ktor("client-cio"))
    testImplementation(ktor("serialization"))
    testImplementation("org.kodein.di:kodein-di-jvm:$kodeinVersion")
    testImplementation("org.javassist:javassist:$javassistVersion")
    testImplementation("org.jacoco:org.jacoco.core:$jacocoVersion")
    testImplementation("org.apache.bcel:bcel:$bcelVersion")
    testImplementation("org.junit.jupiter:junit-jupiter:5.5.2")
    testImplementation("io.kotlintest:kotlintest-runner-junit5:3.3.2")
    testImplementation("io.mockk:mockk:1.9.3")
    testImplementation("com.epam.drill:test-framework:$drillAdminVersion@jar") { isChanging = true }
    testImplementation("com.epam.drill:admin-core:$drillAdminVersion:all@jar") { isChanging = true }
    testImplementation(project(":common"))
    testImplementation(project(":plugin-api-agent"))
    testImplementation(project(":plugin-api-admin"))
    testImplementation(project(":ktor-swagger"))
    testImplementation(project(":test2code-admin"))
    testImplementation(project(":tests"))

    testData(project(":test-data"))
}

kotlin.sourceSets.all {
    languageSettings.optIn("kotlin.Experimental")
    languageSettings.optIn("kotlin.time.ExperimentalTime")
    languageSettings.optIn("kotlinx.serialization.ExperimentalSerializationApi")
}

@Suppress("UNUSED_VARIABLE")
tasks {
    val prepareDistr by registering(Sync::class) {
        from(rootProject.tasks["testDistZip"])
        into("distr/adminStorage")
    }
    val loadTest by registering(Test::class) {
        description = "Runs the loadTest tests"
        group = "verification"
        useJUnitPlatform()
        systemProperty("plugin.config.path", rootDir.resolve("plugin_config.json"))
        systemProperty("drill.plugins.test2code.features.realtime", false)
        systemProperty("analytic.disable", true)
        dependsOn("customBuildClasses")
        dependsOn(prepareDistr)
        mustRunAfter(test)
    }
    //check.get().dependsOn(loadTest)
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
