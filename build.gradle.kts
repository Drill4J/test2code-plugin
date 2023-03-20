@Suppress("RemoveRedundantBackticks")
plugins {
    `distribution`
    kotlin("jvm").apply(false)
    kotlin("multiplatform").apply(false)
    kotlin("plugin.allopen").apply(false)
    kotlin("plugin.noarg").apply(false)
    kotlin("plugin.serialization").apply(false)
    id("kotlinx-atomicfu").apply(false)
    id("org.jetbrains.kotlinx.benchmark").apply(false)
    id("com.github.johnrengelman.shadow").apply(false)
    id("com.github.hierynomus.license").apply(false)
}

group = "com.epam.drill.plugins"

val kotlinVersion: String by extra
val kotlinxCollectionsVersion: String by extra
val kotlinxCoroutinesVersion: String by extra
val kotlinxSerializationVersion: String by extra

repositories {
    mavenLocal()
    mavenCentral()
}

subprojects {
    val constraints = setOf(
        dependencies.constraints.create("org.jetbrains.kotlin:kotlin-reflect:$kotlinVersion"),
        dependencies.constraints.create("org.jetbrains.kotlin:kotlin-stdlib:$kotlinVersion"),
        dependencies.constraints.create("org.jetbrains.kotlin:kotlin-stdlib-common:$kotlinVersion"),
        dependencies.constraints.create("org.jetbrains.kotlin:kotlin-stdlib-jdk7:$kotlinVersion"),
        dependencies.constraints.create("org.jetbrains.kotlin:kotlin-stdlib-jdk8:$kotlinVersion"),
        dependencies.constraints.create("org.jetbrains.kotlinx:kotlinx-collections-immutable:$kotlinxCollectionsVersion"),
        dependencies.constraints.create("org.jetbrains.kotlinx:kotlinx-collections-immutable-jvm:$kotlinxCollectionsVersion"),
        dependencies.constraints.create("org.jetbrains.kotlinx:kotlinx-coroutines-core:$kotlinxCoroutinesVersion"),
        dependencies.constraints.create("org.jetbrains.kotlinx:kotlinx-coroutines-core-jvm:$kotlinxCoroutinesVersion"),
        dependencies.constraints.create("org.jetbrains.kotlinx:kotlinx-coroutines-debug:$kotlinxCoroutinesVersion"),
        dependencies.constraints.create("org.jetbrains.kotlinx:kotlinx-coroutines-jdk8:$kotlinxCoroutinesVersion"),
        dependencies.constraints.create("org.jetbrains.kotlinx:kotlinx-serialization-core:$kotlinxSerializationVersion"),
        dependencies.constraints.create("org.jetbrains.kotlinx:kotlinx-serialization-core-jvm:$kotlinxSerializationVersion"),
        dependencies.constraints.create("org.jetbrains.kotlinx:kotlinx-serialization-json:$kotlinxSerializationVersion"),
        dependencies.constraints.create("org.jetbrains.kotlinx:kotlinx-serialization-json-jvm:$kotlinxSerializationVersion"),
        dependencies.constraints.create("org.jetbrains.kotlinx:kotlinx-serialization-protobuf:$kotlinxSerializationVersion"),
    )
    configurations.all {
        dependencyConstraints += constraints
    }
}

distributions {
    val pluginConfigJson by tasks.creating(Copy::class) {
        group = "distribution"
        val pluginConfigTemplate = file("plugin_config.json")
        val pluginConfigTemporary = file("$buildDir/tmp/${pluginConfigTemplate.name}").apply {
            parentFile.mkdirs()
            writeText(pluginConfigTemplate.readText().replace("{version}", project.version.toString()))
        }
        from(pluginConfigTemporary)
        into("$buildDir/config")
    }
    main.get().contents {
        from(
            tasks.getByPath(":test2code-admin:shadowJar"),
            tasks.getByPath(":test2code-agent:shadowJar"),
            pluginConfigJson
        )
        into("/")
    }
    create("test").contents {
        from(
            tasks.getByPath(":test2code-admin:shadowJar"),
            tasks.getByPath(":test2code-agent:testShadowJar"),
            pluginConfigJson
        )
        into("/")
    }
}
