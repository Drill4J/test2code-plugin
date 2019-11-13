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
    }

    val prepareDist by registering(Copy::class) {
        from(rootProject.tasks["distZip"])
        into(file("distr").resolve("adminStorage"))
    }

    getByPath(":admin-part:testIntegrationClasses").dependsOn(prepareDist)

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
        create<MavenPublication>("coverageZip") {
            artifact(tasks["distZip"])
        }
    }
}

tasks.build {
    dependsOn("publishCoverageZipPublicationToMavenLocal")
}
