import com.github.jengelman.gradle.plugins.shadow.tasks.*

plugins {
    `kotlin-platform-jvm`
    `kotlinx-serialization`
    `kotlinx-atomicfu`
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
    testImplementation("org.junit.jupiter:junit-jupiter:5.5.2")
}

tasks {
    val shadowConfig: ShadowJar.() -> Unit = {
        mergeServiceFiles()
        isZip64 = true
        archiveFileName.set("agent-part.jar")
        from(jar)
        relocateToMainPackage(
            "io.vavr",
            "org.objectweb.asm",
            "org.apache.bcel",
            "org.jacoco.core"
        )
    }

    val agentShadow by registering(ShadowJar::class)
    agentShadow {
        shadowConfig()
        configurations = listOf(agent)
        relocateToMainPackage("kotlin")
    }

    val agentShadowTest by registering(ShadowJar::class)
    agentShadowTest {
        shadowConfig()
        configurations = listOf(commonJarDepsTest)
    }
}

fun ShadowJar.relocateToMainPackage(vararg pkgs: String) = pkgs.forEach {
    relocate(it, "${rootProject.group}.test2code.shadow.$it")
}
