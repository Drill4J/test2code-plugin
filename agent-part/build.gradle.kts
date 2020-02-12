import com.github.jengelman.gradle.plugins.shadow.tasks.*

plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
    `kotlinx-atomicfu`
    id("com.github.johnrengelman.shadow")
}

configurations {
    testImplementation {
        extendsFrom(shadow.get())
    }
}

dependencies {
    shadow(project(":common-part")) { isTransitive = false }
    shadow("org.jacoco:org.jacoco.core:$jacocoVersion")

    implementation(kotlin("stdlib"))

    //provided by drill runtime
    implementation("com.epam.drill:drill-agent-part-jvm:$drillApiVersion")
    implementation("com.epam.drill:common-jvm:$drillApiVersion")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-runtime:$serializationRuntimeVersion")

    compileOnly("org.jetbrains.kotlinx:atomicfu:$atomicFuVersion")

    testImplementation(kotlin("test-junit"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.5.2")
}

tasks {

    fun ShadowJar.commonConfig() {
        isZip64 = true
        archiveFileName.set("agent-part.jar")
        configurations = listOf(project.configurations.shadow.get())
        dependencies {
            exclude(
                "/META-INF/**",
                "/*.class",
                "/*.html"
            )
        }
        relocateToMainPackage(
            "org.objectweb.asm",
            "org.jacoco.core"
        )
    }

    shadowJar {
        commonConfig()
        relocate("kotlin", "kruntime")
        relocate("kotlinx", "kruntimex")
    }
    //TODO remove after fixes in test framework
    val shadowJarTest by registering(ShadowJar::class)
    shadowJarTest {
        group = "shadow"
        from(jar)
        commonConfig()
    }
}

fun ShadowJar.relocateToMainPackage(vararg pkgs: String) = pkgs.forEach {
    relocate(it, "${rootProject.group}.test2code.shadow.$it")
}
