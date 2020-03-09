package com.epam.drill.plugins.test2code

import com.epam.drill.common.*
import kotlinx.serialization.*
import kotlinx.serialization.json.*
import org.jacoco.core.data.*
import java.io.*

//TODO remove this after the data API has been redesigned

suspend fun Test2CodeAdminPart.handleGettingData(params: Map<String, String>): Any = when (params["type"]) {
    "tests-to-run" -> lastTestsToRun.testsToRunDto()
    "recommendations" -> newBuildActionsList()
    "coverage-data" -> { //TODO rewrite or remove this
        val byteArrayOutputStream = ByteArrayOutputStream()
        val buildProbes = pluginInstanceState.scopeManager.scopes(buildVersion)
            .map {
                it.probes.map { it.value.map { it.probes.map { it.value }.flatten() }.flatten() }.flatten()
            }.flatten()
        val dataStore = buildProbes.execDataStore()
        @Suppress("BlockingMethodInNonBlockingContext")
        val writer = ExecutionDataWriter(byteArrayOutputStream)
        val info = SessionInfo(buildVersion, System.currentTimeMillis() - 1000, System.currentTimeMillis())
        writer.visitSessionInfo(info)
        dataStore.accept(writer)
        byteArrayOutputStream.toByteArray()
    }
    else -> {
        if (params.isEmpty()) {
            storeClient.summaryOf(agentId, buildVersion) ?: JsonNull
        } else Unit
    }
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

