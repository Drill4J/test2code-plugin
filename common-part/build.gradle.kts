plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
    `kotlinx-atomicfu`
}

dependencies {
    implementation(kotlin("stdlib"))

    //provided by drill runtime
    implementation("com.epam.drill:common-jvm:$drillCommonVersion")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-runtime:$serializationRuntimeVersion")

    testImplementation(kotlin("test-junit"))
}
