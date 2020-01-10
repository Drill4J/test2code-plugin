plugins {
    distribution
    `maven-publish`
    id("com.epam.drill.version.plugin") apply false
}

allprojects {
    apply(plugin = "com.epam.drill.version.plugin")
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

tasks.build {
    dependsOn("publishTest2codeZipPublicationToMavenLocal")
}
