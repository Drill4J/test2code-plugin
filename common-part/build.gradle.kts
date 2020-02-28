plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
    id("kotlinx-atomicfu")
}

dependencies {
    implementation(kotlin("stdlib"))

    //provided by drill runtime
    implementation("com.epam.drill:common-jvm")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-runtime")

    testImplementation(kotlin("test-junit"))
}
