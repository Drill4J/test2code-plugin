plugins {
    distribution
    `maven-publish`
    id("com.epam.drill.version.plugin") apply false
}

allprojects {
    apply(plugin = "com.epam.drill.version.plugin")
}

tasks {
    val pluginConfigJson = file("plugin_config.json")
    distributions {
        main {
            contents {
                from(getByPath(":admin-part:adminShadow"), getByPath(":agent-part:agentShadow"), pluginConfigJson)
                into("/")
            }
        }
        create("test") {
            contents {
                from(getByPath(":admin-part:adminShadow"), getByPath(":agent-part:agentShadowTest"), pluginConfigJson)
                into("/")
            }
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
