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
    mavenLocal()
    maven(url = "https://oss.jfrog.org/artifactory/list/oss-release-local")
    mavenCentral()
    jcenter()
}

application {
    mainClassName = "io.ktor.server.netty.EngineMain"
    applicationDefaultJvmArgs = appJvmArgs
}

tasks {

    val deleteOldPluginVersions by registering(Delete::class) {
        delete(fileTree("distr/adminStorage") { include("${rootProject.name}-*.zip") })
    }

    (run) {
        dependsOn(":publishTest2codeZipPublicationToMavenLocal")
        dependsOn(deleteOldPluginVersions)
        val normalizedPluginName = rootProject.name.replace("-", "_").toUpperCase()
        environment("${normalizedPluginName}_VERSION", rootProject.version.toString())
    }
}

dependencies {
    runtimeOnly("com.epam.drill:admin-core:$adminReleaseVersion-+:all@jar") { isChanging = true }
}
