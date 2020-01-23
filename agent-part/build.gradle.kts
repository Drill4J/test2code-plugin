import com.github.jengelman.gradle.plugins.shadow.tasks.*

plugins {
    `kotlin-platform-jvm`
    `kotlinx-serialization`
    `kotlinx-atomicfu`
}
val vavrVersion = "0.10.0"
val bcelVersion = "6.3.1"

val commonJarDeps by configurations.creating {}
val agent by configurations.creating {
    extendsFrom(commonJarDeps)
}
dependencies {
    commonJarDeps(project(":common-part"))
    commonJarDeps("org.jacoco:org.jacoco.core:$jacocoVersion")
    commonJarDeps("org.apache.bcel:bcel:$bcelVersion")
}

val commonJarDepsTest by configurations.creating {}
dependencies {
    commonJarDepsTest("org.jacoco:org.jacoco.core:$jacocoVersion")
    commonJarDepsTest("org.apache.bcel:bcel:$bcelVersion")
}

dependencies {
    implementation(kotlin("stdlib"))
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-runtime:$serializationRuntimeVersion")
    implementation("org.jacoco:org.jacoco.core:$jacocoVersion")
    implementation("io.vavr:vavr-kotlin:$vavrVersion")
    compileOnly("org.jetbrains.kotlinx:atomicfu:$atomicFuVersion")
    implementation(project(":common-part"))
    implementation("com.epam.drill:drill-agent-part-jvm:$drillCommonVersion")
    implementation("com.epam.drill:common-jvm:$drillCommonVersion")
    testImplementation(kotlin("test-junit"))
}

tasks {
    val agentShadow by registering(ShadowJar::class)
    agentShadow {
        configurations = listOf(agent)
        configureProd()
        archiveFileName.set("agent-part.jar")
        from(jar)
    }
    val agentShadowTest by registering(ShadowJar::class)
    agentShadowTest {
        configurations = listOf(commonJarDepsTest)
        configureTest()
        archiveFileName.set("agent-part.jar")
        from(jar)
    }
}

fun ShadowJar.configureProd() {
    mergeServiceFiles()
    isZip64 = true
    relocate("io.vavr", "coverage.io.vavr")
    relocate("kotlin", "kruntime")
    relocate("org.apache.bcel", "coverage.org.apache.bcel")
    relocate("org.objectweb.asm", "coverage.org.objectweb.asm")
    relocate("org.jacoco.core", "coverage.org.jacoco.core")
}

fun ShadowJar.configureTest() {
    mergeServiceFiles()
    isZip64 = true
    relocate("org.objectweb.asm", "coverage.org.objectweb.asm")
    relocate("org.jacoco.core", "coverage.org.jacoco.core")
}
