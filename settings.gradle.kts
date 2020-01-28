rootProject.name = "test2code-plugin"

include(":admin-part")
include(":agent-part")
include(":common-part")
include(":tests")

includeBuild("plugin-runner")

buildCache {
    local {
        directory = rootDir.resolve("build-cache")
        removeUnusedEntriesAfterDays = 30
    }
}
