import com.github.jengelman.gradle.plugins.shadow.tasks.*
import org.jetbrains.kotlin.gradle.tasks.*

plugins {
    kotlin("jvm")
    `kotlinx-serialization`
    `kotlinx-atomicfu`
    id("com.github.johnrengelman.shadow") version "5.1.0"
}
val jacocoVersion = "0.8.3"
val vavrVersion = "0.10.0"
val ktorVersion = "1.2.5"
val bcelVersion = "6.3.1"


repositories {
    mavenLocal()
    maven(url = "https://oss.jfrog.org/artifactory/list/oss-release-local")
    mavenCentral()
    jcenter()
}

val commonJarDeps by configurations.creating {}
val adminJarDeps by configurations.creating {
    extendsFrom(commonJarDeps)
}
dependencies {
    commonJarDeps("org.jacoco:org.jacoco.core:$jacocoVersion")
    commonJarDeps("org.apache.bcel:bcel:$bcelVersion")
    commonJarDeps(project(":common-part"))
    adminJarDeps("io.vavr:vavr-kotlin:$vavrVersion")
}


dependencies {
    implementation(kotlin("stdlib"))
    implementation("com.epam.drill:kodux-jvm:0.1.1") {
        isChanging = true
    }
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-runtime:$serializationRuntimeVersion")
    api("org.jetbrains.xodus:xodus-entity-store:1.3.91")
    api(kotlin("stdlib-jdk8"))
    api("com.epam.drill:drill-admin-part-jvm:+")
    implementation(ktor("locations"))
    implementation(project(":common-part"))
    implementation("com.epam.drill:common-jvm:+")

    implementation("org.jacoco:org.jacoco.core:$jacocoVersion")
    implementation("io.vavr:vavr-kotlin:$vavrVersion")

    testImplementation(kotlin("test-junit"))
    testCompile("com.epam.drill:test-framework:+")
    testImplementation("com.epam.drill:admin-core:+")
    testImplementation(ktor("server-test-host"))
    testImplementation(ktor("auth"))
    testImplementation(ktor("auth-jwt"))
    testImplementation(ktor("server-netty"))
    testImplementation(ktor("locations"))
    testImplementation(ktor("server-core"))
    testImplementation(ktor("websockets"))
    testImplementation("org.kodein.di:kodein-di-generic-jvm:6.2.0")
    testImplementation("io.kotlintest:kotlintest-runner-junit5:3.3.2")


}
tasks.withType<KotlinCompile> {
    kotlinOptions.jvmTarget = "1.8"
}

tasks {
    test {
        systemProperty("plugin.config.path", rootDir.resolve("plugin_config.json"))
    }
    val jar by existing(Jar::class)

    val adminShadow by registering(ShadowJar::class) {
        configurations = listOf(adminJarDeps)
//        configurate()
        archiveFileName.set("admin-part.jar")
        from(jar)
    }
}


@Suppress("unused")
fun DependencyHandler.ktor(module: String, version: String? = ktorVersion): Any =
    "io.ktor:ktor-$module${version?.let { ":+" } ?: ""}"
