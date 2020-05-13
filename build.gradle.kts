plugins {
    base
    distribution
    `maven-publish`
    id("com.epam.drill.version.plugin")
    kotlin("jvm") apply false
}

apply(from = "publishing.gradle.kts")

val drillPluginId: String by project

val drillApiVersion: String by project
val atomicFuVersion: String by project
val ktorVersion: String by project
val coroutinesVersion: String by project
val kxSerializationVersion: String by project
val kxCollectionsVersion: String by project

subprojects {
    apply<BasePlugin>()
    apply(plugin = "com.epam.drill.version.plugin")

    group = "${rootProject.group}.$drillPluginId"

    repositories {
        mavenLocal()
        maven(url = "https://oss.jfrog.org/artifactory/list/oss-release-local")
        mavenCentral()
        jcenter()
    }

    val constraints = listOf(
        "com.epam.drill:common-jvm:$drillApiVersion",
        "com.epam.drill:common:$drillApiVersion",
        "com.epam.drill:drill-admin-part:$drillApiVersion",
        "com.epam.drill:drill-agent-part:$drillApiVersion",
        "org.jetbrains.kotlinx:atomicfu:$atomicFuVersion",
        "org.jetbrains.kotlinx:kotlinx-serialization-runtime-common:$kxSerializationVersion",
        "org.jetbrains.kotlinx:kotlinx-serialization-runtime:$kxSerializationVersion",
        "org.jetbrains.kotlinx:kotlinx-serialization-protobuf:$kxSerializationVersion",
        "org.jetbrains.kotlinx:kotlinx-coroutines-core:$coroutinesVersion",
        "org.jetbrains.kotlinx:kotlinx-collections-immutable-jvm:$kxCollectionsVersion",
        "com.epam.drill:kodux-jvm:0.1.8",
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

val prepareConfigJson by tasks.creating(Copy::class) {
    group = "distribution"
    from(provider {
        file("$buildDir/tmp/${pluginConfigJson.name}").apply {
            parentFile.mkdirs()
            val json = pluginConfigJson.readText()
            writeText(json.replace("{version}", "${project.version}"))
        }
    })
    into("$buildDir/config")
}

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
                prepareConfigJson
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
                prepareConfigJson
            )
            into("/")
        }
    }
}

publishing {
    publications {
        create<MavenPublication>("test2codeZip") {
            artifact(tasks.distZip.get())
        }
    }
}
