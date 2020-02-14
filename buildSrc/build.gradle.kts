plugins {
    `kotlin-dsl`
}

repositories {
    mavenLocal()
    maven(url = "https://dl.bintray.com/kotlin/kotlinx/")
    maven(url = "http://oss.jfrog.org/oss-release-local")
    jcenter()
}

val kotlinVersion = "1.3.60"
val atomicFuVersion = "0.14.1"
val drillPluginVersion = "0.15.1"
val shadowPluginVersion = "5.2.0"
dependencies {
    implementation(kotlin("gradle-plugin", kotlinVersion))
    implementation(kotlin("stdlib-jdk8", kotlinVersion))
    implementation(kotlin("serialization", kotlinVersion))
    implementation(kotlin("reflect", kotlinVersion))
    implementation("org.jetbrains.kotlinx:atomicfu-gradle-plugin:$atomicFuVersion")
    implementation("com.epam.drill:gradle-plugin:$drillPluginVersion")
    implementation("com.github.jengelman.gradle.plugins:shadow:$shadowPluginVersion")
}

kotlinDslPluginOptions {
    experimentalWarning.set(false)
}
