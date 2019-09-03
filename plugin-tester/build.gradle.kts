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
    if (version.toString().endsWith("-SNAPSHOT"))
        maven(url = "https://oss.jfrog.org/artifactory/list/oss-snapshot-local")
    else
        maven(url = "https://oss.jfrog.org/artifactory/list/oss-release-local")
    mavenCentral()
    jcenter()
}

application {
    mainClassName = "io.ktor.server.netty.EngineMain"
    applicationDefaultJvmArgs = appJvmArgs
}

dependencies {
    compile(group = "com.epam.drill", name = "admin", version = "0.3.1-SNAPSHOT", classifier = "all")
}

tasks {
    named("run") {
        doFirst {
            copy {
                from(rootProject.tasks["distZip"])
                into(projectDir.resolve("distr").resolve("adminStorage"))
            }
        }
    }
}