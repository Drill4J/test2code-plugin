@Suppress("RemoveRedundantBackticks")
plugins {
    `application`
    `distribution`
    kotlin("jvm").apply(false)
    kotlin("multiplatform").apply(false)
    kotlin("plugin.noarg").apply(false)
    kotlin("plugin.serialization").apply(false)
    id("kotlinx-atomicfu").apply(false)
    id("com.github.johnrengelman.shadow").apply(false)
    id("com.github.hierynomus.license").apply(false)
}

group = "com.epam.drill.plugins"

repositories {
    mavenLocal()
    mavenCentral()
}

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

distributions {
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
