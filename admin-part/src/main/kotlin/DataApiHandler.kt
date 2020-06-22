package com.epam.drill.plugins.test2code

import com.epam.drill.common.*
import kotlinx.serialization.builtins.*

//TODO remove this after the data API has been redesigned

fun Test2CodeAdminPart.handleGettingData(params: Map<String, String>): Any = when (params["type"]) {
    "tests-to-run" -> lastTestsToRun.testsToRunDto()
    "recommendations" -> newBuildActionsList()
    else -> Unit
}

private fun Test2CodeAdminPart.newBuildActionsList(): String {
    val list = mutableListOf<String>()

    if (lastTestsToRun.isNotEmpty()) {
        list.add("Run recommended tests to cover modified methods")
    }

    if (buildInfo?.newMethodsCount() ?: 0 > 0) {
        list.add("Update your tests to cover new methods")
    }
    return String.serializer().list stringify list
}

private fun BuildInfo.newMethodsCount(): Int = methodChanges.map[DiffType.NEW]?.count() ?: 0
