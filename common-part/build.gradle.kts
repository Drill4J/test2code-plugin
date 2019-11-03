plugins {
    kotlin
    `kotlinx-serialization`
    `kotlinx-atomicfu`
}
repositories {
    mavenLocal()
    if (version.toString().endsWith("-SNAPSHOT")) {
        maven(url = "https://oss.jfrog.org/artifactory/list/oss-snapshot-local")
    }
    maven(url = "https://oss.jfrog.org/artifactory/list/oss-release-local")
    mavenCentral()
    jcenter()
}

dependencies {
    implementation("com.epam.drill:common-jvm:$version")
    implementation("org.jetbrains.kotlin:kotlin-stdlib")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-runtime:$serializationRuntimeVersion")
    testImplementation(kotlin("test-junit"))
}