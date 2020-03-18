rootProject.name = "test2code-plugin"

apply(from = "plugins.settings.gradle.kts")

include(":admin-part")
include(":agent-part")
include(":common-part")
include(":tests")
include(":plugin-runner")
