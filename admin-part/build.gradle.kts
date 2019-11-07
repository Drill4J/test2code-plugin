import com.github.jengelman.gradle.plugins.shadow.tasks.*
import org.jetbrains.kotlin.gradle.plugin.*
import org.jetbrains.kotlin.gradle.tasks.*

plugins {
    java
    kotlin("jvm")
    `kotlinx-serialization`
    `kotlinx-atomicfu`
    idea
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
val integrationTestImplementation by configurations.creating {
    extendsFrom(configurations["testCompile"])
}

val testData by configurations.creating {}
val integrationTestRuntime by configurations.creating {
    extendsFrom(configurations["testRuntime"])
}

dependencies {
    commonJarDeps("org.jacoco:org.jacoco.core:$jacocoVersion")
    commonJarDeps("org.apache.bcel:bcel:$bcelVersion")
    commonJarDeps(project(":common-part"))
    adminJarDeps("io.vavr:vavr-kotlin:$vavrVersion")
    add("testData", "com.epam.drill:test-data:$drillVersion")
}


dependencies {
    implementation(kotlin("stdlib"))
    implementation("com.epam.drill:kodux-jvm:0.1.1") {
        isChanging = true
    }
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-runtime:$serializationRuntimeVersion")
    api("org.jetbrains.xodus:xodus-entity-store:1.3.91")
    api(kotlin("stdlib-jdk8"))
    api("com.epam.drill:drill-admin-part-jvm:$drillVersion")
    implementation("com.epam.drill:drill-agent-part-jvm:$drillVersion")
    implementation(ktor("locations"))
    implementation(project(":common-part"))
    implementation("com.epam.drill:common-jvm:$drillVersion")

    implementation("org.jacoco:org.jacoco.core:$jacocoVersion")
    implementation("io.vavr:vavr-kotlin:$vavrVersion")

    testImplementation(kotlin("test-junit"))
    testImplementation("org.kodein.di:kodein-di-generic-jvm:6.2.0")
    integrationTestImplementation(kotlin("test-junit"))
    integrationTestImplementation("com.epam.drill:test-framework:$drillVersion")
    integrationTestImplementation("com.epam.drill:admin-core:$drillVersion")
    integrationTestImplementation(ktor("server-test-host"))
    integrationTestImplementation(ktor("auth"))
    integrationTestImplementation(ktor("auth-jwt"))
    integrationTestImplementation(ktor("server-netty"))
    integrationTestImplementation(ktor("locations"))
    integrationTestImplementation(ktor("server-core"))
    integrationTestImplementation(ktor("websockets"))
    integrationTestImplementation(project(":agent-part"))
    integrationTestImplementation("io.mockk:mockk:1.9.3")
    integrationTestImplementation("io.kotlintest:kotlintest-runner-junit5:3.3.2")
    integrationTestImplementation("org.junit.jupiter:junit-jupiter:5.5.2")
    integrationTestImplementation("org.apache.bcel:bcel:$bcelVersion")


}

val testIngerationModuleName = "test-integration"

sourceSets {
    create(testIngerationModuleName) {
        withConvention(KotlinSourceSet::class) {
            kotlin.srcDir("src/$testIngerationModuleName/kotlin")
            resources.srcDir("src/$testIngerationModuleName/resources")
            compileClasspath += sourceSets["main"].output + integrationTestImplementation + configurations["testRuntimeClasspath"]
            runtimeClasspath += output + compileClasspath + sourceSets["test"].runtimeClasspath + integrationTestRuntime
        }
    }

    (1..2).forEach {
        create("build$it") {
            java.srcDir("src/test-data/build$it/java")
            dependencies {
                implementation("com.epam.drill:test-data:$drillVersion")
            }
            compileClasspath += testData
            runtimeClasspath += output + compileClasspath
        }
        tasks["testIntegrationClasses"].dependsOn("build${it}Classes")
    }
}


idea {
    module {
        testSourceDirs =
            (sourceSets[testIngerationModuleName].withConvention(KotlinSourceSet::class) { kotlin.srcDirs })
        testResourceDirs = (sourceSets[testIngerationModuleName].resources.srcDirs)
        scopes["TEST"]?.get("plus")?.add(integrationTestImplementation)
    }
}

task<Test>("integrationTest") {
    useJUnitPlatform()
    systemProperty("plugin.config.path", rootDir.resolve("plugin_config.json"))
    description = "Runs the integration tests"
    group = "verification"
    testClassesDirs = sourceSets[testIngerationModuleName].output.classesDirs
    classpath = sourceSets[testIngerationModuleName].runtimeClasspath
    mustRunAfter(tasks["test"])
}

tasks.named("check") {
    dependsOn("integrationTest")
}
tasks.withType<KotlinCompile> {
    kotlinOptions.jvmTarget = "1.8"
    kotlinOptions.freeCompilerArgs += "-Xuse-experimental=io.ktor.locations.KtorExperimentalLocationsAPI"
}
tasks {
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

fun DependencyHandler.integrationTestImplementation(dependencyNotation: Any): Dependency? =
    add("integrationTestImplementation", dependencyNotation)