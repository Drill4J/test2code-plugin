package com.epam.drill.plugins.test2code

//TODO remove this after the data API has been redesigned

fun Test2CodeAdminPart.handleGettingData(params: Map<String, String>): Any = when (params["type"]) {
    "tests-to-run" -> pluginInstanceState.run {
        buildTests[buildId(buildVersion)]?.testsToRun ?: emptyMap()
    }.testsToRunDto()
    "recommendations" -> newBuildActionsList()
    else -> Unit
}

private fun Test2CodeAdminPart.newBuildActionsList(): Set<String> = pluginInstanceState.run {
    val buildId = buildId(buildVersion)
    coverages[buildId]?.recommendations(buildTests[buildId]?.testsToRun ?: emptyMap())
} ?: emptySet()
