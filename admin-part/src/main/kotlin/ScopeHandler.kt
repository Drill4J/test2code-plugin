package com.epam.drill.plugins.test2code

import com.epam.drill.plugin.api.end.*
import com.epam.drill.plugins.test2code.api.*
import com.epam.drill.plugins.test2code.api.routes.*
import com.epam.drill.plugins.test2code.common.api.*
import com.epam.drill.plugins.test2code.storage.*

internal fun Plugin.initActiveScope(): Boolean = activeScope.init { sendSessions, sessions ->
    if (sendSessions) {
        sendActiveSessions()
    }
    sessions?.let {
        val context = state.coverContext()
        val bundleCounters = sessions.calcBundleCounters(context)
        val coverageInfoSet = bundleCounters.calculateCoverageData(context, this)
        updateSummary { it.copy(coverage = coverageInfoSet.coverage as ScopeCoverage) }
        sendActiveScope()
        coverageInfoSet.sendScopeCoverage(buildVersion, id)
    }
}

internal suspend fun Plugin.changeActiveScope(
    scopeChange: ActiveScopeChangePayload
): ActionResult = if (state.scopeByName(scopeChange.scopeName) == null) {
    val prevScope = state.changeActiveScope(scopeChange.scopeName.trim())
    state.storeActiveScopeInfo()
    storeClient.deleteSessions(prevScope.id)
    sendActiveSessions()
    sendActiveScope()
    if (scopeChange.savePrevScope) {
        if (prevScope.any()) {
            val context = state.coverContext()
            val counters = prevScope.calcBundleCounters(context)
            val coverData = counters.calculateCoverageData(context, prevScope)
            val finishedScope = prevScope.finish(scopeChange.prevScopeEnabled).run {
                copy(
                    data = data.copy(bundleCounters = counters),
                    summary = summary.copy(coverage = coverData.coverage as ScopeCoverage)
                )
            }
            state.scopeManager.store(finishedScope)
            sendScopeSummary(finishedScope.summary)
            coverData.sendScopeCoverage(buildVersion, finishedScope.id)
            logger.info { "$finishedScope has been saved." }
        } else {
            cleanTopics(prevScope.id)
            logger.info { "$prevScope is empty, it won't be added to the build." }
        }
    } else cleanTopics(prevScope.id)
    InitActiveScope(
        payload = InitScopePayload(
            id = activeScope.id,
            name = activeScope.name,
            prevId = prevScope.id
        )
    ).toActionResult()
} else ActionResult(
    code = StatusCodes.CONFLICT,
    data = "Failed to switch to a new scope: name ${scopeChange.scopeName} is already in use"
)

internal suspend fun Plugin.scopeInitialized(prevId: String) {
    if (initActiveScope()) {
        sendScopes()
        val prevScope = state.scopeManager.byId(prevId)
        prevScope?.takeIf { it.enabled }.apply {
            calculateAndSendBuildCoverage()
        }
        calculateAndSendScopeCoverage()
        logger.info { "Current active scope - $activeScope" }
    }
}

internal suspend fun Plugin.renameScope(
    payload: RenameScopePayload
): ActionResult = state.scopeById(payload.scopeId)?.let { scope ->
    val scopeName = payload.scopeName
    if (state.scopeByName(scopeName) == null) {
        state.renameScope(payload.scopeId, scopeName)?.let { summary ->
            sendScopeMessages(scope.buildVersion)
            sendScopeSummary(summary, scope.buildVersion)
        }
        ActionResult(StatusCodes.OK, "Renamed scope with id ${payload.scopeId} -> $scopeName")
    } else ActionResult(
        StatusCodes.CONFLICT,
        "Scope with such name already exists. Please choose a different name."
    )
} ?: ActionResult(
    StatusCodes.NOT_FOUND,
    "Failed to rename scope with id ${payload.scopeId}: scope not found"
)

internal suspend fun Plugin.toggleScope(scopeId: String): ActionResult {
    state.toggleScope(scopeId)
    return state.scopeManager.byId(scopeId)?.let { scope ->
        handleChange(scope)
        ActionResult(
            StatusCodes.OK,
            "Scope with id $scopeId toggled to 'enabled' value '${scope.enabled}'"
        )
    } ?: ActionResult(
        StatusCodes.NOT_FOUND,
        "Failed to toggle scope with id $scopeId: scope not found"
    )
}

internal suspend fun Plugin.dropScope(scopeId: String): ActionResult {
    return state.scopeManager.deleteById(scopeId)?.let { scope ->
        cleanTopics(scope.id)
        handleChange(scope)
        ActionResult(
            StatusCodes.OK,
            "Scope with id $scopeId was removed"
        )
    } ?: ActionResult(
        StatusCodes.NOT_FOUND,
        "Failed to drop scope with id $scopeId: scope not found"
    )
}

private suspend fun Plugin.cleanTopics(scopeId: String) = Routes.Scope(scopeId).let { scope ->
    send(buildVersion, Routes.Scope.AssociatedTests(scope), "")
    val coverageRoute = Routes.Scope.Coverage(scope)
    send(buildVersion, coverageRoute, "")
    state.classDataOrNull()?.let { classData ->
        val pkgsRoute = Routes.Scope.Coverage.Packages(coverageRoute)
        classData.packageTree.packages.forEach {
            send(buildVersion, Routes.Scope.Coverage.Packages.Package(it.name, pkgsRoute), "")
        }
    }
    send(buildVersion, Routes.Scope.Tests(scope), "")
}

private suspend fun Plugin.handleChange(scope: FinishedScope) {
    if (scope.buildVersion == buildVersion) {
        calculateAndSendBuildCoverage()
        activeScope.probesChanged()
    }
    sendScopes(scope.buildVersion)
    sendScopeSummary(scope.summary, scope.buildVersion)
}
