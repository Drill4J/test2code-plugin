plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
    id("kotlinx-atomicfu")
    id("com.github.johnrengelman.shadow")
}

val jarDeps by configurations.creating
configurations.implementation {
    extendsFrom(jarDeps)
}

dependencies {
    jarDeps(project(":common-part")) { isTransitive = false }
    jarDeps("org.jacoco:org.jacoco.core")

    implementation(kotlin("stdlib"))

    //provided by drill runtime
    implementation("com.epam.drill:drill-agent-part-jvm")
    implementation("com.epam.drill:common-jvm")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-runtime")

    compileOnly("org.jetbrains.kotlinx:atomicfu")

    testImplementation(kotlin("test-junit5"))
    testImplementation("org.junit.jupiter:junit-jupiter")
}

tasks {
    test {
        useJUnitPlatform()
    }

    fun com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar.commonConfig() {
        isZip64 = true
        archiveFileName.set("agent-part.jar")
        configurations = listOf(jarDeps)
        dependencies {
            exclude(
                "/META-INF/**",
                "/*.class",
                "/*.html"
            )
        }
        listOf(
            "org.objectweb.asm",
            "org.jacoco.core"
        ).forEach { relocate(it, "${rootProject.group}.test2code.shadow.$it") }
    }

    shadowJar {
        commonConfig()
        relocate("kotlin", "kruntime")
        relocate("kotlinx", "kruntimex")
    }
    //TODO remove after fixes in test framework
    val shadowJarTest by registering(com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar::class)
    shadowJarTest {
        group = "shadow"
        from(jar)
        commonConfig()
    }
}
