plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
    id("kotlinx-atomicfu")
    id("com.github.johnrengelman.shadow")
}

val jarDeps by configurations.creating {
    attributes.attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage.JAVA_API))
}
configurations.implementation {
    extendsFrom(jarDeps)
}

val drillDsmVersion: String by extra
val testContainerVersion: String by project
val muLogger: String by project
kotlin.sourceSets.all {
    languageSettings.optIn("kotlinx.coroutines.ExperimentalCoroutinesApi")
    languageSettings.optIn("kotlinx.serialization.ExperimentalSerializationApi")
}

dependencies {
    implementation(kotlin("stdlib-jdk8"))

    //plugin dependencies
    jarDeps(project(":api"))
    jarDeps(project(":agent-api"))
    jarDeps(project(":jacoco"))
    jarDeps("org.jacoco:org.jacoco.core")
    jarDeps("org.apache.bcel:bcel")

    //provided by drill runtime
    implementation("com.epam.drill:drill-admin-part")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-protobuf")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core")
    compileOnly("org.jetbrains.kotlinx:atomicfu")
    compileOnly("com.github.luben:zstd-jni")

    //provided by admin
    //TODO create a platform for admin dependencies
    implementation("com.epam.drill.dsm:core:$drillDsmVersion")
    //TODO replace with drill logging - EPMDJ-9548
    implementation("io.github.microutils:kotlin-logging-jvm:$muLogger")
    implementation("org.jetbrains.kotlinx:kotlinx-collections-immutable")

    testImplementation(kotlin("test-junit5"))
    testImplementation("com.epam.drill.dsm:test-framework:$drillDsmVersion")
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.jetbrains.kotlinx:atomicfu")
}

tasks {
    test {
        useJUnitPlatform()
        environment("DSM_FETCH_AND_PUSH_LIMIT" to 5000)
    }

    shadowJar {
        destinationDirectory.set(file("$buildDir/shadowLibs"))
        archiveFileName.set("admin-part.jar")
        isZip64 = true
        configurations = listOf(jarDeps)
        dependencies {
            exclude(
                "/META-INF/**",
                "/*.class",
                "/*.html"
            )
        }
        listOf(
            "org.jacoco",
            "org.objectweb"
        ).forEach { relocate(it, "${rootProject.group}.test2code.shadow.$it") }
    }
}
