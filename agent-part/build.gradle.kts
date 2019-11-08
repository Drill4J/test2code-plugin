import com.github.jengelman.gradle.plugins.shadow.tasks.*

plugins {
    kotlin("jvm")
    `kotlinx-serialization`
    `kotlinx-atomicfu`
    id("com.github.johnrengelman.shadow") version "5.1.0"
}
val jacocoVersion = "0.8.3"
val vavrVersion = "0.10.0"
val bcelVersion = "6.3.1"

repositories {
    mavenLocal()
    maven(url = "https://oss.jfrog.org/artifactory/list/oss-release-local")
    mavenCentral()
    jcenter()
}

val commonJarDeps by configurations.creating {}
val agent by configurations.creating {
    extendsFrom(commonJarDeps)
}
dependencies {
    commonJarDeps(project(":common-part"))
    commonJarDeps("org.jacoco:org.jacoco.core:$jacocoVersion")
    commonJarDeps("org.apache.bcel:bcel:$bcelVersion")
}
dependencies {
    implementation(kotlin("stdlib"))
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-runtime:$serializationRuntimeVersion")
    implementation("org.jacoco:org.jacoco.core:$jacocoVersion")
    implementation("io.vavr:vavr-kotlin:$vavrVersion")
    compileOnly("org.jetbrains.kotlinx:atomicfu:$atomicFuVersion")
    implementation(project(":common-part"))
    implementation("com.epam.drill:drill-agent-part-jvm:$drillVersion")
    implementation("com.epam.drill:common-jvm:$drillVersion")
    testImplementation(kotlin("test-junit"))
    implementation("io.github.microutils:kotlin-logging:1.6.24")
}

tasks {
    val jar by existing(Jar::class)
    val agentShadow by registering(ShadowJar::class) {
        configurations = listOf(agent)
        configurate()
        archiveFileName.set("agent-part.jar")
        from(jar)
    }
}

fun ShadowJar.configurate() {
    mergeServiceFiles()
    isZip64 = true
    relocate("io.vavr", "coverage.io.vavr")
    relocate("kotlin", "kruntime")
    relocate("org.apache.bcel", "coverage.org.apache.bcel")
    relocate("org.objectweb.asm", "coverage.org.objectweb.asm")
    relocate("org.jacoco.core", "coverage.org.jacoco.core")
}