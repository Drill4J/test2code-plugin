plugins {
    application
}

val appJvmArgs = listOf(
    "-server",
    "-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=5006",
    "-Djava.awt.headless=true",
    "-Xms128m",
    "-Xmx2g",
    "-XX:+UseG1GC",
    "-XX:MaxGCPauseMillis=100"
)

application {
    mainClassName = "io.ktor.server.netty.EngineMain"
    applicationDefaultJvmArgs = appJvmArgs
}

tasks {
    val cleanData by registering(Delete::class) {
        group = "build"
        delete("work", "distr")
    }

    clean {
        dependsOn(cleanData)
    }

    val syncDistro by registering(Sync::class) {
        from(rootProject.tasks.distZip)
        into("distr/adminStorage")
    }

    (run) {
        dependsOn(syncDistro)
        environment("DRILL_DEVMODE", true)
        environment("DRILL_PLUGINS_REMOTE_ENABLED", false)
    }
}

val drillAdminVersion: String by rootProject

dependencies {
    runtimeOnly("com.epam.drill:admin-core:$drillAdminVersion:all@jar")
}
