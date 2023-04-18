plugins {
    application
}

group = "com.epam.drill.plugins.test2code"
version = rootProject.version

val drillAdminVersion: String by rootProject

repositories {
    mavenLocal()
    mavenCentral()
}

configurations.all {
    resolutionStrategy.cacheDynamicVersionsFor(5, TimeUnit.MINUTES)
}

dependencies {
    runtimeOnly("com.epam.drill:admin-core:$drillAdminVersion:all@jar")
}

application {
    mainClass.set("io.ktor.server.netty.EngineMain")
    applicationDefaultJvmArgs = listOf(
        "-Xms128m",
        "-Xmx2g",
        "-server",
        "-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=5006",
        "-Djava.awt.headless=true",
        "-XX:+UseG1GC",
        "-XX:+UseStringDeduplication"
    )
}

tasks {
    val prepareDistr by registering(Sync::class) {
        from(rootProject.tasks.distZip)
        into("distr/adminStorage")
    }
    (run) {
        environment("DRILL_DEVMODE", true)
        environment("DRILL_DEFAULT_PACKAGES", "org/springframework/samples/petclinic,com/epam,package")
        environment("DRILL_AGENTS_SOCKET_TIMEOUT", 360)
        environment("DRILL_PLUGINS_REMOTE_ENABLED", false)
        systemProperty("analytic.disable", true)
        dependsOn(prepareDistr)
    }
    clean {
        delete("distr")
        delete("work")
    }
}
