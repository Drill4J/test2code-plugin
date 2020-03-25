rootProject.name = "test2code-plugin"

apply(from = "plugins.settings.gradle.kts")

include(":common")
include(":api")
include(":admin-part")
include(":agent-part")
include(":tests")
include(":plugin-runner")
