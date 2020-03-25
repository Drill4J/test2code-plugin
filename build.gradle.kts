plugins {
    base
    distribution
    `maven-publish`
    id("com.epam.drill.version.plugin")
    kotlin("jvm") apply false
}

val drillApiVersion: String by project
val atomicFuVersion: String by project
val ktorVersion: String by project

subprojects {
    apply<BasePlugin>()
    apply(plugin = "com.epam.drill.version.plugin")

    repositories {
        mavenLocal()
        maven(url = "https://oss.jfrog.org/artifactory/list/oss-release-local")
        mavenCentral()
        jcenter()
    }

    val constraints = listOf(
        "com.epam.drill:common-jvm:$drillApiVersion",
        "com.epam.drill:drill-admin-part-jvm:$drillApiVersion",
        "com.epam.drill:drill-agent-part-jvm:$drillApiVersion",
        "org.jetbrains.kotlinx:atomicfu:$atomicFuVersion",
        "org.jetbrains.kotlinx:kotlinx-serialization-runtime:0.20.0",
        "org.jetbrains.kotlinx:kotlinx-serialization-cbor:0.20.0",
        "org.jetbrains.kotlinx:kotlinx-coroutines-core:1.3.4",
        "org.jetbrains.kotlinx:kotlinx-collections-immutable-jvm:0.3.1",
        "com.epam.drill:kodux-jvm:0.1.6",
        "org.jetbrains.xodus:xodus-entity-store:1.3.91",
        "io.ktor:ktor-locations:$ktorVersion",
        "org.jacoco:org.jacoco.core:0.8.5",
        "org.junit.jupiter:junit-jupiter:5.5.2"
    ).map(dependencies.constraints::create)

    configurations.all {
        dependencyConstraints += constraints
    }

    tasks {
        withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
            kotlinOptions {
                jvmTarget = "1.8"
                allWarningsAsErrors = true
                freeCompilerArgs = listOf(
                    "-Xuse-experimental=kotlin.Experimental",
                    "-Xuse-experimental=kotlin.time.ExperimentalTime"
                )
            }
        }
    }
}

val pluginConfigJson = file("plugin_config.json")
distributions {
    val adminShadow = provider {
        tasks.getByPath(":admin-part:shadowJar")
    }

    val agentShadow = provider {
        tasks.getByPath(":agent-part:shadowJar")
    }

    val agentShadowTest = provider {
        tasks.getByPath(":agent-part:shadowJarTest")
    }

    main {
        contents {
            from(
                adminShadow,
                agentShadow,
                pluginConfigJson
            )
            into("/")
        }
    }

    //TODO Remove: there should be no special distro for integration tests
    create("test") {
        contents {
            from(
                adminShadow,
                agentShadowTest,
                pluginConfigJson
            )
            into("/")
        }
    }
}

publishing {
    repositories {
        maven {
            url = uri("http://oss.jfrog.org/oss-release-local")
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
        create<MavenPublication>("test2codeZip") {
            artifact(tasks["distZip"])
        }
    }
}
