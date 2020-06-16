plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
}

val testBuilds = listOf("build1", "build2")
sourceSets {
    testBuilds.forEach { create(it) }
}

val testData: Configuration by configurations.creating {}

configurations {
    all { resolutionStrategy.cacheDynamicVersionsFor(5, TimeUnit.MINUTES) }
    testImplementation {
        extendsFrom(testData)
    }
    testBuilds.forEach {
        named("${it}Implementation") {
            extendsFrom(testData)
        }
    }
}

val drillAdminVersion: String by rootProject
val ktorVersion: String by rootProject
val ktorSwaggerVersion: String by rootProject

dependencies {
    testCompileOnly(project(":api"))
    testImplementation(project(":admin-part"))
    testCompileOnly(project(":agent-part"))

    testImplementation("com.epam.drill:common")
    testImplementation("com.epam.drill:drill-agent-part")
    testImplementation("com.epam.drill:drill-admin-part")
    testImplementation("org.jetbrains.kotlinx:kotlinx-serialization-runtime")
    testImplementation("org.jetbrains.kotlinx:atomicfu")
    testImplementation("org.jetbrains.kotlinx:kotlinx-collections-immutable-jvm")

    testImplementation("com.epam.drill:test-framework:$drillAdminVersion") { isChanging = true }
    testImplementation("com.epam.drill:admin-core:$drillAdminVersion") { isChanging = true }

    testImplementation("org.kodein.di:kodein-di-generic-jvm:6.2.0")

    testImplementation("com.epam.drill.ktor:ktor-swagger:$ktorSwaggerVersion")
    testImplementation(ktor("server-test-host"))
    testImplementation(ktor("auth"))
    testImplementation(ktor("auth-jwt"))
    testImplementation(ktor("server-netty"))
    testImplementation(ktor("locations"))
    testImplementation(ktor("server-core"))
    testImplementation(ktor("websockets"))
    testImplementation(ktor("client-cio"))
    testImplementation(ktor("serialization"))

    testImplementation("com.epam.drill:kodux-jvm")
    testImplementation("org.jetbrains.xodus:xodus-entity-store")

    testImplementation("org.jacoco:org.jacoco.core")
    testImplementation("org.apache.bcel:bcel:6.3.1")
    testImplementation("io.vavr:vavr-kotlin:0.10.0") //TODO remove

    testImplementation(kotlin("test-junit5"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("io.kotlintest:kotlintest-runner-junit5:3.3.2")
    testImplementation("io.mockk:mockk:1.9.3")

    testData("com.epam.drill:test-data:$drillAdminVersion") { isChanging = true }
}

tasks {
    val testBuildClassesTasks = testBuilds.map { named("${it}Classes") }

    val distrDir = file("distr")

    clean {
        delete(distrDir)
    }

    val prepareDist by registering(Sync::class) {
        from(rootProject.tasks.named("testDistZip"))
        into(distrDir.resolve("adminStorage"))
    }

    val integrationTest by registering(Test::class) {
        description = "Runs the integration tests"
        group = "verification"
        dependsOn(testBuildClassesTasks.toTypedArray())
        dependsOn(prepareDist)
        useJUnitPlatform()
        systemProperty("plugin.config.path", rootDir.resolve("plugin_config.json"))
        systemProperty("plugin.feature.drealtime", false)
        mustRunAfter(test)
    }

    check {
        dependsOn(integrationTest)
    }
}

@Suppress("unused")
fun DependencyHandler.ktor(module: String, version: String? = ktorVersion): Any =
    "io.ktor:ktor-$module:${version ?: "+"}"
