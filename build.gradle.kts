plugins {
    base
    distribution
    `maven-publish`
    kotlin("jvm") apply false
    id("com.github.hierynomus.license")
    id("org.jetbrains.kotlin.plugin.noarg")
    id("io.github.gradle-nexus.publish-plugin") version "1.1.0"
}

val drillPluginId: String by project

val scriptUrl: String by extra

val drillApiVersion: String by project
val atomicFuVersion: String by project
val ktorVersion: String by project
val coroutinesVersion: String by project
val kxSerializationVersion: String by project
val kxCollectionsVersion: String by project
val zstdVersion: String by extra
val jacocoVersion: String by extra
val bcelVersion: String by extra

//TODO remove this block and gradle/classes dir after gradle is updated to v6.8
buildscript {
    dependencies {
        classpath(files("gradle/classes"))
    }
}

allprojects {
    apply(from = rootProject.uri("$scriptUrl/git-version.gradle.kts"))
    repositories {
        apply(from = rootProject.uri("$scriptUrl/maven-repo.gradle.kts"))
        mavenLocal()
        mavenCentral()
        jcenter()
    }
}

subprojects {
    apply<BasePlugin>()
    apply(plugin = "org.jetbrains.kotlin.plugin.noarg")

    group = "${rootProject.group}.$drillPluginId"

    val constraints = listOf(
        "com.epam.drill:common:0.8.0-12-env.0",
        "com.epam.drill:drill-admin-part:$drillApiVersion",
        "com.epam.drill:drill-agent-part:$drillApiVersion",
        "org.jetbrains.kotlinx:atomicfu:$atomicFuVersion",
        "org.jetbrains.kotlinx:kotlinx-serialization-core:$kxSerializationVersion",
        "org.jetbrains.kotlinx:kotlinx-serialization-json:$kxSerializationVersion",
        "org.jetbrains.kotlinx:kotlinx-serialization-protobuf:$kxSerializationVersion",
        "org.jetbrains.kotlinx:kotlinx-coroutines-core:$coroutinesVersion",
        "org.jetbrains.kotlinx:kotlinx-collections-immutable:$kxCollectionsVersion",
        "org.jetbrains.kotlinx:kotlinx-collections-immutable-jvm:$kxCollectionsVersion",
        "io.ktor:ktor-locations:$ktorVersion",
        "com.github.luben:zstd-jni:$zstdVersion",
        "org.jacoco:org.jacoco.core:$jacocoVersion",
        "org.apache.bcel:bcel:$bcelVersion",
        "org.junit.jupiter:junit-jupiter:5.5.2"
    ).map(dependencies.constraints::create)

    configurations.all {
        dependencyConstraints += constraints
    }

    noArg {
        annotation("kotlinx.serialization.Serializable")
    }

    tasks {
        withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
            kotlinOptions {
                jvmTarget = "1.8"
                freeCompilerArgs = listOf(
                    "-Xuse-experimental=kotlin.Experimental",
                    "-Xuse-experimental=kotlinx.serialization.ExperimentalSerializationApi",
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

val licenseFormatSettings by tasks.registering(com.hierynomus.gradle.license.tasks.LicenseFormat::class) {
    source = fileTree(project.projectDir).also {
        include("**/*.kt", "**/*.java", "**/*.groovy", "**/*.sql")
        exclude("**/.idea")
    }.asFileTree
    headerURI = java.net.URI("https://raw.githubusercontent.com/Drill4J/drill4j/develop/COPYRIGHT")
}

tasks["licenseFormat"].dependsOn(licenseFormatSettings)
