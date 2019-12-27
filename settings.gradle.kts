rootProject.name = "coverage-plugin"
include(":admin-part")
include(":agent-part")
include(":common-part")
include(":plugin-tester")

buildCache {
    local {
        directory = rootDir.resolve("build-cache")
        removeUnusedEntriesAfterDays = 30
    }
}