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

dependencies {
    implementation(kotlin("stdlib-jdk8"))

    //plugin dependencies
    jarDeps(project(":api"))
    jarDeps("org.jacoco:org.jacoco.core")

    //provided by drill runtime
    implementation("com.epam.drill:common")
    implementation("com.epam.drill:drill-admin-part")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-runtime")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-cbor")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-protobuf")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core")
    compileOnly("org.jetbrains.kotlinx:atomicfu")

    //provided by admin
    //TODO create a platform for admin dependencies
    implementation("com.epam.drill:kodux-jvm")
    implementation("org.jetbrains.xodus:xodus-entity-store")
    implementation("org.jetbrains.kotlinx:kotlinx-collections-immutable-jvm")

    testImplementation(kotlin("test-junit5"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.jetbrains.kotlinx:atomicfu")
}

tasks {
    test {
        useJUnitPlatform()
    }

    shadowJar {
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

    withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
        kotlinOptions { freeCompilerArgs += "-Xuse-experimental=kotlinx.coroutines.ExperimentalCoroutinesApi" }
    }
}
