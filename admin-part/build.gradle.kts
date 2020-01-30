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
    implementation(kotlin("stdlib-jdk8"))

    //plugin dependencies
    shadow(project(":common-part")) { isTransitive = false }
    shadow("org.jacoco:org.jacoco.core:$jacocoVersion")

    //provided by drill runtime
    implementation("com.epam.drill:drill-admin-part-jvm:$drillCommonVersion")
    implementation("com.epam.drill:common-jvm:$drillCommonVersion")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-runtime:$serializationRuntimeVersion")

    //provided by admin
    //TODO create a platform for admin dependencies
    implementation("com.epam.drill:kodux-jvm:$koduxVersion")
    implementation("org.jetbrains.xodus:xodus-entity-store:$xodusVersion")
    implementation("io.ktor:ktor-locations:$ktorVersion")
    implementation("org.jetbrains.kotlinx:kotlinx-collections-immutable-jvm:0.3")

    testImplementation(kotlin("test-junit"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.5.2")
    testImplementation("org.jetbrains.kotlinx:atomicfu:$atomicFuVersion")
}

tasks {
    shadowJar {
        archiveFileName.set("admin-part.jar")
        isZip64 = true
        configurations = listOf(project.configurations.shadow.get())
        dependencies {
            exclude(
                "/META-INF/**",
                "/*.class",
                "/*.html"
            )
        }
        relocateToMainPackage(
            "org.jacoco",
            "org.objectweb"
        )
    }
}

fun ShadowJar.relocateToMainPackage(vararg pkgs: String) = pkgs.forEach {
    relocate(it, "${rootProject.group}.test2code.shadow.$it")
}
