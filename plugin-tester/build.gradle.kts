plugins {
    java
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

repositories {
    if (version.toString().endsWith("-SNAPSHOT")) {
        maven(url = "https://oss.jfrog.org/artifactory/list/oss-snapshot-local")
    }
    maven(url = "https://oss.jfrog.org/artifactory/list/oss-release-local")
    mavenCentral()
    jcenter()
}

application {
    mainClassName = "io.ktor.server.netty.EngineMain"
    applicationDefaultJvmArgs = appJvmArgs
}

dependencies {
    runtime("com.epam.drill:admin:0.3.0:all@jar")
}

tasks {
    val prepareDist by registering(Copy::class) {
        from(rootProject.tasks["distZip"])
        into(file("distr").resolve("adminStorage"))
    }

    named("run") {
        dependsOn(prepareDist)
    }
}