buildscript {
    repositories {
        gradlePluginPortal()
        maven(url = "https://oss.jfrog.org/artifactory/list/oss-release-local")
    }

    dependencies {
        classpath("com.epam.drill:gradle-plugin:0.9.0")
    }
}
