plugins {
    java
    kotlin("jvm")
    `kotlinx-serialization`
}

val testBuilds = listOf("build1", "build2")
sourceSets {
    testBuilds.forEach { create(it) }
}

val testData: Configuration by configurations.creating {}

configurations {
    testImplementation {
        extendsFrom(testData)
    }
    testBuilds.forEach {
        named("${it}Implementation") {
            extendsFrom(testData)
        }
    }
}

dependencies {
    testImplementation(project(":admin-part"))
    testCompileOnly(project(":common-part"))
    testCompileOnly(project(":agent-part"))

    testImplementation("com.epam.drill:drill-admin-part-jvm:$drillCommonVersion")
    testImplementation("com.epam.drill:common-jvm:$drillCommonVersion")

    testImplementation("org.kodein.di:kodein-di-generic-jvm:6.2.0")
    testImplementation("org.jetbrains.kotlinx:atomicfu:$atomicFuVersion")

    testImplementation("com.epam.drill:test-framework:$drillAdminVersion")
    testImplementation("com.epam.drill:admin-core:$drillAdminVersion")
    testImplementation("com.epam.drill:drill-agent-part-jvm:$drillCommonVersion")

    testImplementation("com.epam.drill:ktor-swagger:$swaggerVersion")
    testImplementation(ktor("server-test-host"))
    testImplementation(ktor("auth"))
    testImplementation(ktor("auth-jwt"))
    testImplementation(ktor("server-netty"))
    testImplementation(ktor("locations"))
    testImplementation(ktor("server-core"))
    testImplementation(ktor("websockets"))
    testImplementation(ktor("client-cio"))
    testImplementation(ktor("serialization"))

    testImplementation("com.epam.drill:kodux-jvm:$koduxVersion")
    testImplementation("org.jetbrains.kotlinx:kotlinx-serialization-runtime:$serializationRuntimeVersion")
    testImplementation("org.jetbrains.xodus:xodus-entity-store:$xodusVersion")

    testImplementation("org.jacoco:org.jacoco.core:$jacocoVersion")
    testImplementation("org.apache.bcel:bcel:$bcelVersion")
    testImplementation("io.vavr:vavr-kotlin:$vavrVersion")
    testImplementation("org.jetbrains.kotlinx:kotlinx-collections-immutable-jvm:0.3")

    testImplementation(kotlin("test-junit"))
    testImplementation("io.kotlintest:kotlintest-runner-junit5:3.3.2")
    testImplementation("org.junit.jupiter:junit-jupiter:5.5.2")
    testImplementation("io.mockk:mockk:1.9.3")

    testData("com.epam.drill:test-data:$drillAdminVersion")
}

tasks {

    val testBuildClassesTasks = testBuilds.map { named("${it}Classes") }

    val prepareDist by registering(Copy::class) {
        from(rootProject.tasks.named("testDistZip"))
        into(file("distr").resolve("adminStorage"))
    }

    val integrationTest by registering(Test::class) {
        description = "Runs the integration tests"
        group = "verification"
        dependsOn(testBuildClassesTasks.toTypedArray())
        dependsOn(prepareDist)
        useJUnitPlatform()
        systemProperty("plugin.config.path", rootDir.resolve("plugin_config.json"))
        mustRunAfter(test)
    }

    check {
        dependsOn(integrationTest)
    }
}

@Suppress("unused")
fun DependencyHandler.ktor(module: String, version: String? = ktorVersion): Any =
    "io.ktor:ktor-$module:${version ?: "+"}"
