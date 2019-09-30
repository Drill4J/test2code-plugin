plugins {
    `kotlin-dsl`
}

repositories {
    mavenLocal()
    maven(url = "https://dl.bintray.com/kotlin/kotlinx/")
    maven(url = "http://oss.jfrog.org/oss-release-local")
    jcenter()
}

val kotlinVersion = "1.3.50"
val atomicFuVersion = "0.12.6"
dependencies {
    implementation(kotlin("gradle-plugin", kotlinVersion))
    implementation(kotlin("stdlib-jdk8", kotlinVersion))
    implementation(kotlin("serialization", kotlinVersion))
    implementation(kotlin("reflect", kotlinVersion))
    implementation("org.jetbrains.kotlinx:atomicfu-gradle-plugin:$atomicFuVersion")
    implementation("com.palantir.gradle.gitversion:gradle-git-version:0.12.2")
    implementation("com.epam.drill:drill-gradle-plugin:0.1.0")
}

kotlinDslPluginOptions {
    experimentalWarning.set(false)
}