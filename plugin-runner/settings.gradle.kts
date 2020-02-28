pluginManagement {
    org.gradle.util.GUtil.loadProperties(
        rootDir.parentFile.resolve(Project.GRADLE_PROPERTIES)
    ).forEach { k, v ->  extra["$k"] = v }
    val drillGradlePluginVersion: String? by extra
    plugins {
        id("com.epam.drill.version") version drillGradlePluginVersion
    }
    repositories {
        gradlePluginPortal()
        maven(url = "https://oss.jfrog.org/artifactory/list/oss-release-local")
    }
}
