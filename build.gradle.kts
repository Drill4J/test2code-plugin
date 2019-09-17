import com.epam.drill.build.*
import com.github.jengelman.gradle.plugins.shadow.tasks.*

plugins {
    `kotlin-multiplatform`
    `kotlinx-serialization`
    `kotlinx-atomicfu`
    distribution
    `maven-publish`
    id("com.github.johnrengelman.shadow") version "5.1.0"
}

repositories {
    if (version.toString().endsWith("-SNAPSHOT")) {
        maven(url = "https://oss.jfrog.org/artifactory/list/oss-snapshot-local")
    }
    maven(url = "https://oss.jfrog.org/artifactory/list/oss-release-local")
    mavenCentral()
    jcenter()
}

val commonJarDeps by configurations.creating {}

val agentJarDeps by configurations.creating {
    extendsFrom(commonJarDeps)
}

val adminJarDeps by configurations.creating {
    extendsFrom(commonJarDeps)
}

dependencies {
    commonJarDeps("org.jacoco:org.jacoco.core:$jacocoVersion")
    commonJarDeps("org.apache.bcel:bcel:$bcelVersion")
    adminJarDeps("io.vavr:vavr-kotlin:$vavrVersion")
    adminJarDeps("org.jetbrains.xodus:dnq:$xodusDnqVersion")
}


kotlin {
    val jvms = listOf(
        jvm(),
        jvm("adminPart"),
        jvm("agentPart")
    )



    sourceSets {
        named("commonMain") {
            dependencies {
                implementation("com.epam.drill:common-jvm:$drillCommonLibVerison")
                implementation("org.jetbrains.kotlin:kotlin-stdlib-common")
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-runtime-common:$serializationRuntimeVersion")
            }
        }
        named("commonTest") {
            dependencies {
                implementation(kotlin("test-common"))
                implementation(kotlin("test-annotations-common"))
            }
        }
        val jvmMain by getting {
            project.configurations.named(implementationConfigurationName) {
                extendsFrom(commonJarDeps)
            }
        }
        val agentPartMain by getting {
            project.configurations.named(implementationConfigurationName) {
                extendsFrom(agentJarDeps)
            }
            dependencies {
                implementation(kotlin("stdlib"))
                implementation("com.epam.drill:drill-agent-part-jvm:$version")
            }
        }
        agentPartMain.dependsOn(jvmMain)
        val adminPartMain by getting {
            project.configurations.named(implementationConfigurationName) {
                extendsFrom(adminJarDeps)
            }
            dependencies {
                implementation(kotlin("stdlib-jdk8"))
                implementation("com.epam.drill:drill-admin-part-jvm:$version")
                implementation("io.vavr:vavr-kotlin:$vavrVersion")
            }
        }
        adminPartMain.dependsOn(jvmMain)

        //common jvm deps
        jvms.forEach {
            it.compilations["main"].defaultSourceSet {
                dependencies {
                    implementation("com.epam.drill:common:$drillCommonLibVerison")

                    implementation("org.jetbrains.kotlinx:kotlinx-serialization-runtime:$serializationRuntimeVersion")
                    compileOnly("org.jetbrains.kotlinx:atomicfu:$atomicFuVersion")
                }
            }
            it.compilations["test"].defaultSourceSet {
                dependencies {
                    implementation("org.jetbrains.kotlinx:atomicfu:$atomicFuVersion")
                    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:$jvmCoroutinesVersion")
                    implementation(kotlin("test-junit"))
                }
            }
        }


    }
}

tasks {
    val pluginConfigJson = file("plugin_config.json")

    fun Configuration.flattenJars() = this.map { if (it.isDirectory) it else zipTree(it) }

    val adminPartJar by existing(Jar::class) {

        from(adminJarDeps.flattenJars())
    }
    val agentPartJar by existing(Jar::class) {

        from(agentJarDeps.flattenJars())
    }
    val adminShadow by registering(ShadowJar::class) {
        configurate()
        archiveFileName.set("admin-part.jar")
        from(adminPartJar)
    }
    val agentShadow by registering(ShadowJar::class) {
        configurate()
        archiveFileName.set("agent-part.jar")
        from(agentPartJar)
    }

    distributions {
        main {
            contents {
                from(adminShadow, agentShadow, pluginConfigJson)
                into("/")
            }
        }
    }
}

fun ShadowJar.configurate() {
    mergeServiceFiles()
    isZip64 = true
    relocate("io.vavr", "coverage.io.vavr")
    relocate("org.apache.bcel", "coverage.org.apache.bcel")
    relocate("org.objectweb.asm", "coverage.org.objectweb.asm")
    relocate("org.jacoco.core", "coverage.org.jacoco.core")
}

publishing {
    repositories {
        maven {
            url =
                if (version.toString().endsWith("-SNAPSHOT"))
                    uri("http://oss.jfrog.org/oss-snapshot-local")
                else uri("http://oss.jfrog.org/oss-release-local")
            credentials {
                username =
                    if (project.hasProperty("bintrayUser"))
                        project.property("bintrayUser").toString()
                    else System.getenv("BINTRAY_USER")
                password =
                    if (project.hasProperty("bintrayApiKey"))
                        project.property("bintrayApiKey").toString()
                    else System.getenv("BINTRAY_API_KEY")
            }
        }
    }

    publications {
        create<MavenPublication>("coverageZip") {
            artifact(tasks["distZip"])
        }
    }
}

tasks.build.get().dependsOn("publishCoverageZipPublicationToMavenLocal")