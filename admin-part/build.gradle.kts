import com.github.jengelman.gradle.plugins.shadow.tasks.*

plugins {
    `kotlin-platform-jvm`
    `kotlinx-serialization`
    `kotlinx-atomicfu`
}

dependencies {
    implementation(kotlin("stdlib-jdk8"))

    implementation(project(":common-part"))
    compileOnly("com.epam.drill:drill-admin-part-jvm:$drillCommonVersion")
    compileOnly("com.epam.drill:common-jvm:$drillCommonVersion")

    compileOnly("com.epam.drill:kodux-jvm:$koduxVersion")
    compileOnly("org.jetbrains.kotlinx:kotlinx-serialization-runtime:$serializationRuntimeVersion")
    compileOnly("org.jetbrains.xodus:xodus-entity-store:$xodusVersion")
    compileOnly("io.ktor:ktor-locations:$ktorVersion")

    implementation("org.jacoco:org.jacoco.core:$jacocoVersion")
    implementation("io.vavr:vavr-kotlin:$vavrVersion")
    implementation("org.jetbrains.kotlinx:kotlinx-collections-immutable-jvm:0.3")

    testImplementation(kotlin("test-junit"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.5.2")
    testImplementation("org.jetbrains.kotlinx:atomicfu:$atomicFuVersion")
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
            exclude("/META-INF/**")
            exclude("/*.class")
            exclude("/*.html")
            exclude(dependency("com.epam.drill:"))
            exclude(dependency("org.jetbrains.kotlin:"))
            exclude(dependency("org.jetbrains.kotlinx:kotlinx-serialization-runtime:"))
            exclude(dependency("org.jetbrains:annotations:"))
        }
        relocateToMainPackage(
            "io.vavr",
            "kotlinx.collections.immutable",
            "org.jacoco",
            "org.objectweb"
        )
    }
}

fun ShadowJar.relocateToMainPackage(vararg pkgs: String) = pkgs.forEach {
    relocate(it, "${rootProject.group}.test2code.shadow.$it")
}
