import com.github.jengelman.gradle.plugins.shadow.tasks.*

plugins {
    `kotlin-platform-jvm`
    `kotlinx-serialization`
    `kotlinx-atomicfu`
}

dependencies {
    implementation(project(":common-part"))

    implementation(kotlin("stdlib"))

    implementation("com.epam.drill:drill-agent-part-jvm:$drillCommonVersion")
    implementation("com.epam.drill:common-jvm:$drillCommonVersion")

    implementation("org.jetbrains.kotlinx:kotlinx-serialization-runtime:$serializationRuntimeVersion")
    compileOnly("org.jetbrains.kotlinx:atomicfu:$atomicFuVersion")

    implementation("org.jacoco:org.jacoco.core:$jacocoVersion")
    implementation("io.vavr:vavr-kotlin:$vavrVersion")

    testImplementation(kotlin("test-junit"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.5.2")
}

tasks {
    fun ShadowJar.commonConfig() {
        group = "shadow"
        isZip64 = true
        archiveFileName.set("agent-part.jar")
        mergeServiceFiles()
        from(jar)
        configurations = listOf(project.configurations.runtimeClasspath.get())
        dependencies {
            exclude("/META-INF/**")
            exclude("/*.class")
            exclude("/*.html")
            exclude("/kotlinx/atomicfu/**") //TODO find more straightforward way
            //FIXME get rid of this mess!!!
            exclude(dependency("com.epam.drill:"))
            exclude(dependency("org.jetbrains.kotlin:"))
            exclude(dependency("org.jetbrains:annotations:"))
            exclude(dependency("org.jetbrains.kotlinx:kotlinx-coroutines-core:"))
            exclude(dependency("org.jetbrains.kotlinx:kotlinx-serialization-runtime:"))
            exclude(dependency("org.jetbrains.kotlinx:kotlinx-serialization-runtime-common:"))
        }
        relocateToMainPackage(
            "io.vavr",
            "org.objectweb.asm",
            "org.apache.bcel",
            "org.jacoco.core"
        )
    }
    val agentShadow by registering(ShadowJar::class)
    agentShadow {
        commonConfig()
        relocate("kotlin", "kruntime")
        relocate("kotlinx", "kruntimex")
    }
    //TODO remove after fixes in test framework
    val agentShadowTest by registering(ShadowJar::class)
    agentShadowTest {
        commonConfig()
    }
}

fun ShadowJar.relocateToMainPackage(vararg pkgs: String) = pkgs.forEach {
    relocate(it, "${rootProject.group}.test2code.shadow.$it")
}
