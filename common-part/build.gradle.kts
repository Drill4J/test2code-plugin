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
    compileOnly("com.epam.drill:common-jvm:$drillCommonVersion")
    compileOnly("org.jetbrains.kotlin:kotlin-stdlib")
    compileOnly("org.jetbrains.kotlinx:kotlinx-serialization-runtime:$serializationRuntimeVersion")
    testImplementation(kotlin("test-junit"))
}
