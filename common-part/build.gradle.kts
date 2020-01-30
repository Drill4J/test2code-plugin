plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
    `kotlinx-atomicfu`
}

dependencies {
    implementation("com.epam.drill:common-jvm:$drillCommonVersion")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-runtime:$serializationRuntimeVersion")
    implementation(kotlin("stdlib"))
    testImplementation(kotlin("test-junit"))
}
