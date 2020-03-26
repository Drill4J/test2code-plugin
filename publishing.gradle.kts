allprojects {
    plugins.withType<MavenPublishPlugin> {
        the<PublishingExtension>().repositories {
            maven {
                url = uri("http://oss.jfrog.org/oss-release-local")
                credentials {
                    username = propOrEnv("bintrayUser", "BINTRAY_USER")
                    password = propOrEnv("bintrayApiKey", "BINTRAY_API_KEY")
                }
            }
        }
    }
}

fun Project.propOrEnv(
    propKey: String, envKey: String
): String? = findProperty(propKey)?.toString() ?: System.getenv(envKey)
