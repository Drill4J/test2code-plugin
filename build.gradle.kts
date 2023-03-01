plugins {
    kotlin("jvm").apply(false)
    id("com.github.hierynomus.license").apply(false)
}

group = "com.epam.drill.plugins"

repositories {
    mavenLocal()
    mavenCentral()
}
