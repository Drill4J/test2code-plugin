import java.net.URI
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import com.hierynomus.gradle.license.tasks.LicenseCheck
import com.hierynomus.gradle.license.tasks.LicenseFormat
import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
    kotlin("jvm")
    kotlin("plugin.noarg")
    kotlin("plugin.serialization")
    id("kotlinx-atomicfu")
    id("com.github.hierynomus.license")
    id("com.github.johnrengelman.shadow")
}

group = "com.epam.drill.plugins"

val kotlinxCollectionsVersion: String by parent!!.extra
val kotlinxCoroutinesVersion: String by parent!!.extra
val kotlinxSerializationVersion: String by parent!!.extra
val jacocoVersion: String by parent!!.extra
val atomicfuVersion: String by parent!!.extra
val lubenZstdVersion: String by parent!!.extra

repositories {
    mavenLocal()
    mavenCentral()
}

val jarDependencies by configurations.creating {
    attributes.attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage.JAVA_API))
}
configurations.implementation.get().extendsFrom(jarDependencies)

dependencies {
    jarDependencies("org.jetbrains.kotlinx:kotlinx-collections-immutable-jvm:$kotlinxCollectionsVersion") { isTransitive = false }
    jarDependencies("org.jacoco:org.jacoco.core:$jacocoVersion")
    jarDependencies("com.github.luben:zstd-jni:$lubenZstdVersion")
    jarDependencies(project(":jacoco")) { isTransitive = false }
    jarDependencies(project(":test2code-common"))

    compileOnly("org.jetbrains.kotlinx:atomicfu:$atomicfuVersion")

    implementation(kotlin("stdlib"))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:$kotlinxCoroutinesVersion")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:$kotlinxSerializationVersion")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-protobuf:$kotlinxSerializationVersion")
    implementation(project(":plugin-api-agent"))

    testImplementation(kotlin("test-junit5"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.jetbrains.kotlinx:atomicfu:$atomicfuVersion")
    //testImplementation(project(":tests"))
}

@Suppress("UNUSED_VARIABLE")
tasks {
    test {
        useJUnitPlatform()
        systemProperty("plugin.feature.drealtime", false)
    }
    withType<KotlinCompile> {
        kotlinOptions.jvmTarget = "1.8"
        kotlinOptions.freeCompilerArgs += "-Xuse-experimental=kotlin.Experimental"
        kotlinOptions.freeCompilerArgs += "-Xuse-experimental=kotlin.ExperimentalStdlibApi"
        kotlinOptions.freeCompilerArgs += "-Xuse-experimental=kotlin.time.ExperimentalTime"
        kotlinOptions.freeCompilerArgs += "-Xuse-experimental=kotlinx.coroutines.ExperimentalCoroutinesApi"
        kotlinOptions.freeCompilerArgs += "-Xuse-experimental=kotlinx.coroutines.FlowPreview"
        kotlinOptions.freeCompilerArgs += "-Xuse-experimental=kotlinx.coroutines.InternalCoroutinesApi"
        kotlinOptions.freeCompilerArgs += "-Xuse-experimental=kotlinx.coroutines.ObsoleteCoroutinesApi"
        kotlinOptions.freeCompilerArgs += "-Xuse-experimental=kotlinx.serialization.ExperimentalSerializationApi"
    }
    val configureShadowJar: ShadowJar.() -> Unit = {
        isZip64 = true
        configurations = listOf(jarDependencies)
        archiveFileName.set("test2code-agent.jar")
        destinationDirectory.set(file("$buildDir/shadowLibs"))
        dependencies {
            exclude("/META-INF/**", "/*.class", "/*.html")
        }
        relocate("org.jacoco.core", "$group.test2code.shadow.org.jacoco.core")
        relocate("org.objectweb.asm", "$group.test2code.shadow.org.objectweb.asm")
        relocate("kotlinx.collections.immutable", "$group.test2code.shadow.kotlinx.collections.immutable")
    }
    shadowJar {
        configureShadowJar()
        relocate("kotlin", "kruntime")
        relocate("kotlinx", "kruntimex")
    }
    val testShadowJar by registering(ShadowJar::class) {
        configureShadowJar()
        group = "shadow"
        from(jar)
    }
}

noArg {
    annotation("kotlinx.serialization.Serializable")
}

@Suppress("UNUSED_VARIABLE")
license {
    headerURI = URI("https://raw.githubusercontent.com/Drill4J/drill4j/develop/COPYRIGHT")
    val licenseFormatSources by tasks.registering(LicenseFormat::class) {
        source = fileTree("$projectDir/src").also {
            include("**/*.kt", "**/*.java", "**/*.groovy")
        }
    }
    val licenseCheckSources by tasks.registering(LicenseCheck::class) {
        source = fileTree("$projectDir/src").also {
            include("**/*.kt", "**/*.java", "**/*.groovy")
        }
    }
}
