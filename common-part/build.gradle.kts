plugins {
    kotlin
    `kotlinx-serialization`
    `kotlinx-atomicfu`
}
repositories {
    mavenLocal()
    maven(url = "https://oss.jfrog.org/artifactory/list/oss-release-local")
    mavenCentral()
    jcenter()
}

dependencies {
    implementation("com.epam.drill:common-jvm:+")
    implementation("org.jetbrains.kotlin:kotlin-stdlib")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-runtime:$serializationRuntimeVersion")
    testImplementation(kotlin("test-junit"))
}