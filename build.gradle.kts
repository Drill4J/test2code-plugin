plugins {
    base
    distribution
    `maven-publish`
    id("com.epam.drill.version.plugin") apply false
}

allprojects {
    apply(plugin = "com.epam.drill.version.plugin")
}

subprojects {
    apply(plugin = "org.gradle.base")

    repositories {
        mavenLocal()
        maven(url = "https://oss.jfrog.org/artifactory/list/oss-release-local")
        mavenCentral()
        jcenter()
    }


    tasks {
        withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
            kotlinOptions {
                jvmTarget = "1.8"
                allWarningsAsErrors = true
                freeCompilerArgs = listOf(
                    "-Xuse-experimental=kotlin.Experimental",
                    "-Xuse-experimental=kotlin.time.ExperimentalTime"
                )
            }
        }

        clean {
            delete("distr", "work")
        }
    }
}

val pluginConfigJson = file("plugin_config.json")
distributions {
    val adminShadow = provider {
        tasks.getByPath(":admin-part:adminShadow")
    }

    val agentShadow = provider {
        tasks.getByPath(":agent-part:agentShadow")
    }

    val agentShadowTest = provider {
        tasks.getByPath(":agent-part:agentShadowTest")
    }

    main {
        contents {
            from(
                adminShadow,
                agentShadow,
                pluginConfigJson
            )
            into("/")
        }
    }

    create("test") {
        contents {
            from(
                adminShadow,
                agentShadowTest,
                pluginConfigJson
            )
            into("/")
        }
    }
}

publishing {
    repositories {
        maven {
            url = uri("http://oss.jfrog.org/oss-release-local")
            credentials {
                username =
                    if (project.hasProperty("bintrayUser"))
                        project.property("bintrayUser").toString()
                    else System.getenv("BINTRAY_USER")
                password =
                    if (project.hasProperty("bintrayApiKey"))
                        project.property("bintrayApiKey").toString()
                    else System.getenv("BINTRAY_API_KEY")
            }
        }
    }

    publications {
        create<MavenPublication>("test2codeZip") {
            artifact(tasks["distZip"])
        }
    }
}

tasks {
    register("run-admin") {
        group = "application"
        dependsOn(publishToMavenLocal)
        dependsOn(gradle.includedBuild("plugin-runner").task(":run"))
    }
}
