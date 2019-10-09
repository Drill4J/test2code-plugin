import com.github.jengelman.gradle.plugins.shadow.tasks.*
import org.jetbrains.kotlin.gradle.tasks.*

plugins {
    `kotlin-multiplatform`
    `kotlinx-serialization`
    `kotlinx-atomicfu`
    distribution
    `maven-publish`
    id("com.github.johnrengelman.shadow") version "5.1.0"
    id("com.epam.drill.version.plugin") apply false
}

allprojects {
    apply(plugin = "com.epam.drill.version.plugin")
}

repositories {
    mavenLocal()
    if (version.toString().endsWith("-SNAPSHOT")) {
        maven(url = "https://oss.jfrog.org/artifactory/list/oss-snapshot-local")
    }
    maven(url = "https://oss.jfrog.org/artifactory/list/oss-release-local")
    mavenCentral()
    jcenter()
}

val jacocoVersion = "0.8.3"
val vavrVersion = "0.10.0"
val bcelVersion = "6.3.1"

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
                implementation("com.epam.drill:common-jvm:$drillCommonLibVersion")
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
                api("com.epam.drill:drill-agent-part-jvm:$drillPluginApiVersion")
            }
        }
        agentPartMain.dependsOn(jvmMain)
        val adminPartMain by getting {
            project.configurations.named(implementationConfigurationName) {
                extendsFrom(adminJarDeps)
            }
            dependencies {
                implementation("com.epam.drill:kodux-jvm:0.1.1"){
                    isChanging = true
                }
                api("org.jetbrains.xodus:xodus-entity-store:1.3.91")
                api(kotlin("stdlib-jdk8"))
                api("com.epam.drill:drill-admin-part-jvm:$drillPluginApiVersion")
                implementation("io.vavr:vavr-kotlin:$vavrVersion")
            }
        }
        adminPartMain.dependsOn(jvmMain)

        //common jvm deps
        jvms.forEach {
            it.compilations["main"].defaultSourceSet {
                dependencies {
                    implementation("org.jetbrains.kotlinx:kotlinx-serialization-runtime:$serializationRuntimeVersion")
                    compileOnly("org.jetbrains.kotlinx:atomicfu:$atomicFuVersion")
                }
            }
            it.compilations["test"].defaultSourceSet {
                dependencies {
                    implementation("org.jetbrains.kotlinx:atomicfu:$atomicFuVersion")
                    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:$jvmCoroutinesVersion")
                    implementation(kotlin("test-junit"))
                    implementation(kotlin("reflect"))
                }
            }
        }


    }
}

tasks {
    val pluginConfigJson = file("plugin_config.json")

    val adminPartJar by existing(Jar::class)
    
    val adminShadow by registering(ShadowJar::class) {
        configurations = listOf(adminJarDeps)
        configurate()
        archiveFileName.set("admin-part.jar")
        from(adminPartJar)
    }

    val agentPartJar by existing(Jar::class)

    val agentShadow by registering(ShadowJar::class) {
        configurations = listOf(agentJarDeps)
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

    withType<KotlinCompile> {
        kotlinOptions.jvmTarget = "1.8"
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

tasks.build {
    dependsOn("publishCoverageZipPublicationToMavenLocal")
}
