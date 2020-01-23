import com.github.jengelman.gradle.plugins.shadow.tasks.*
import org.jetbrains.kotlin.gradle.plugin.*

plugins {
    java
    `kotlin-platform-jvm`
    `kotlinx-serialization`
    `kotlinx-atomicfu`
}

val integrationTestImplementation by configurations.creating {
    extendsFrom(configurations.compileClasspath.get())
    extendsFrom(configurations.testCompileClasspath.get())
    attributes {
        attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage::class, Usage.JAVA_RUNTIME))
    }
}

val testData by configurations.creating {}

dependencies {
    implementation(kotlin("stdlib-jdk8"))

    implementation(project(":common-part"))
    compileOnly("com.epam.drill:drill-admin-part-jvm:$drillCommonVersion")
    compileOnly("com.epam.drill:common-jvm:$drillCommonVersion")

    compileOnly("com.epam.drill:kodux-jvm:$koduxVersion")
    compileOnly("org.jetbrains.kotlinx:kotlinx-serialization-runtime:$serializationRuntimeVersion")
    compileOnly("org.jetbrains.xodus:xodus-entity-store:$xodusVersion")
    compileOnly(ktor("locations"))

    implementation("org.jacoco:org.jacoco.core:$jacocoVersion")
    implementation("io.vavr:vavr-kotlin:$vavrVersion")
    implementation("org.jetbrains.kotlinx:kotlinx-collections-immutable-jvm:0.3")

    testImplementation(kotlin("test-junit"))
    testImplementation("org.kodein.di:kodein-di-generic-jvm:6.2.0")
    testImplementation("org.jetbrains.kotlinx:atomicfu:$atomicFuVersion")
    integrationTestImplementation(kotlin("test-junit"))
    integrationTestImplementation("com.epam.drill:test-framework:$drillAdminVersion")
    integrationTestImplementation("com.epam.drill:admin-core:$drillAdminVersion")
    integrationTestImplementation("com.epam.drill:drill-agent-part-jvm:$drillCommonVersion")
    integrationTestImplementation(ktor("server-test-host"))
    integrationTestImplementation(ktor("auth"))
    integrationTestImplementation(ktor("auth-jwt"))
    integrationTestImplementation(ktor("server-netty"))
    integrationTestImplementation(ktor("locations"))
    integrationTestImplementation(ktor("server-core"))
    integrationTestImplementation(ktor("websockets"))
    integrationTestImplementation(ktor("client-cio"))
    integrationTestImplementation(ktor("serialization"))
    integrationTestImplementation("com.epam.drill:ktor-swagger:$swaggerVersion")
    integrationTestImplementation(project(":agent-part"))
    integrationTestImplementation("io.mockk:mockk:1.9.3")
    integrationTestImplementation("io.kotlintest:kotlintest-runner-junit5:3.3.2")
    integrationTestImplementation("org.junit.jupiter:junit-jupiter:5.5.2")
    integrationTestImplementation("org.apache.bcel:bcel:$bcelVersion")
    testData("com.epam.drill:test-data:$drillAdminVersion")
}

val testIntegrationModuleName = "test-integration"

sourceSets {
    create(testIntegrationModuleName) {
        withConvention(KotlinSourceSet::class) {
            kotlin.srcDir("src/$testIntegrationModuleName/kotlin")
            resources.srcDir("src/$testIntegrationModuleName/resources")
            compileClasspath += sourceSets.main.get().output + integrationTestImplementation
            runtimeClasspath += output + compileClasspath + configurations.testRuntimeClasspath.get()
        }
    }

    (1..2).forEach {
        create("build$it") {
            java.srcDir("src/test-data/build$it/java")
            dependencies {
                implementation("com.epam.drill:test-data:$drillAdminVersion")
            }
            compileClasspath += testData
            runtimeClasspath += output + compileClasspath
        }
        tasks["testIntegrationClasses"].dependsOn("build${it}Classes")
    }
}


tasks {
    val adminShadow by registering(ShadowJar::class)
    adminShadow {
        group = "shadow"
        archiveFileName.set("admin-part.jar")
        isZip64 = true
        from(jar)
        configurations = listOf(project.configurations.runtimeClasspath.get())
        dependencies {
            exclude("META-INF/**")
            exclude(dependency("com.epam.drill:"))
            exclude(dependency("org.jetbrains.kotlin:"))
            exclude(dependency("org.jetbrains.kotlinx:kotlinx-serialization-runtime:"))
            exclude(dependency("org.jetbrains:annotations:"))
        }
        val shadowPackagePrefix = "${rootProject.group}.test2code.shadow."
        val forReplacement = listOf(
            "io.vavr",
            "kotlinx.collections.immutable",
            "org.jacoco",
            "org.objectweb"
        )
        for (pattern in forReplacement) {
            relocate(pattern, "$shadowPackagePrefix.$pattern.")
        }
        exclude("module-info.class")
    }

    val prepareDist by registering(Copy::class) {
        from(rootProject.tasks.named("testDistZip"))
        into(file("distr").resolve("adminStorage"))
    }

    named("testIntegrationClasses") {
        dependsOn(prepareDist)
    }

    val integrationTest by registering(Test::class) {
        useJUnitPlatform()
        systemProperty("plugin.config.path", rootDir.resolve("plugin_config.json"))
        description = "Runs the integration tests"
        group = "verification"
        testClassesDirs = sourceSets[testIntegrationModuleName].output.classesDirs
        classpath = sourceSets[testIntegrationModuleName].runtimeClasspath
        mustRunAfter(test)
    }

    check {
        dependsOn(integrationTest)
    }

    clean {
        delete("distr", "work")
    }
}

@Suppress("unused")
fun DependencyHandler.ktor(module: String, version: String? = ktorVersion): Any =
    "io.ktor:ktor-$module:${version ?: "+"}"

fun DependencyHandler.integrationTestImplementation(dependencyNotation: Any): Dependency? =
    add("integrationTestImplementation", dependencyNotation)
