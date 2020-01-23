plugins {
    kotlin
    `kotlinx-serialization`
    `kotlinx-atomicfu`
}

dependencies {
    compileOnly("com.epam.drill:common-jvm:$drillCommonVersion")
    compileOnly("org.jetbrains.kotlin:kotlin-stdlib")
    compileOnly("org.jetbrains.kotlinx:kotlinx-serialization-runtime:$serializationRuntimeVersion")
    testImplementation(kotlin("test-junit"))
}
