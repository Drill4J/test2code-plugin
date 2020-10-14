package com.epam.drill.plugins.test2code

import com.epam.drill.plugin.api.message.*
import com.epam.drill.plugins.test2code.api.*
import com.epam.drill.plugins.test2code.api.routes.*
import com.epam.drill.plugins.test2code.common.api.*
import com.epam.drill.plugins.test2code.coverage.*
import com.epam.drill.plugins.test2code.storage.*
import mu.*

private val logger = KotlinLogging.logger {}

internal suspend fun Plugin.initActiveScope() {
    val parentContext = state.parentContext()
    if (runtimeConfig.realtime) {
        activeScope.subscribeOnChanges { sessions ->
            val context = state.coverContext()
            updateSummary { it.calculateCoverage(sessions, context) }
            sendScopeMessages()
            val bundleCounters = sessions.calcBundleCounters(context)
            val coverageInfoSet = bundleCounters.calculateCoverageData(context, this, parentContext)
            coverageInfoSet.sendScopeCoverage(buildVersion, this.id)
        }
    }
}

internal suspend fun Plugin.changeActiveScope(
    scopeChange: ActiveScopeChangePayload
): Any = if (state.scopeByName(scopeChange.scopeName) == null) {
    val prevScope = state.changeActiveScope(scopeChange.scopeName.trim())
    state.storeActiveScopeInfo()
    storeClient.deleteSessions(prevScope.id)
    sendActiveSessions()
    sendActiveScope()
    if (scopeChange.savePrevScope) {
        if (prevScope.any()) {
            val finishedScope = prevScope.finish(scopeChange.prevScopeEnabled).run {
                val bundleCounters = calcBundleCounters(state.coverContext())
                copy(data = data.copy(bundleCounters = bundleCounters))
            }
            state.scopeManager.store(finishedScope)
            logger.info { "$finishedScope has been saved." }
        } else logger.info { "$prevScope is empty, it won't be added to the build." }
    }
    InitActiveScope(
        payload = InitScopePayload(
            id = activeScope.id,
            name = activeScope.name,
            prevId = prevScope.id
        )
    )
} else StatusMessage(
    StatusCodes.CONFLICT,
    "Failed to switch to a new scope: name ${scopeChange.scopeName} is already in use"
)

internal suspend fun Plugin.scopeInitialized(prevId: String) {
    initActiveScope()
    val prevScope = state.scopeManager.byId(prevId)
    prevScope?.apply {
        sendScopeSummary(summary)
    } ?: cleanTopics(prevId)
    sendScopes()
    prevScope?.takeIf { it.enabled }.apply {
        calculateAndSendBuildCoverage()
    }
    calculateAndSendScopeCoverage()
    logger.info { "Current active scope - $activeScope" }
}

internal suspend fun Plugin.renameScope(
    payload: RenameScopePayload
): StatusMessage = state.scopeById(payload.scopeId)?.let { scope ->
    val scopeName = payload.scopeName
    if (scope.summary.name != scopeName && state.scopeByName(scopeName) == null) {
        state.renameScope(payload.scopeId, scopeName)
        sendScopeMessages(scope.buildVersion)
        sendScopeSummary(scope.summary, scope.buildVersion)
        StatusMessage(StatusCodes.OK, "Renamed scope with id ${payload.scopeId} -> $scopeName")
    } else StatusMessage(
        StatusCodes.CONFLICT,
        "Scope with such name already exists. Please choose a different name."
    )
} ?: StatusMessage(
    StatusCodes.NOT_FOUND,
    "Failed to rename scope with id ${payload.scopeId}: scope not found"
)

internal suspend fun Plugin.toggleScope(scopeId: String): StatusMessage {
    state.toggleScope(scopeId)
    return state.scopeManager.byId(scopeId)?.let { scope ->
        handleChange(scope)
        StatusMessage(
            StatusCodes.OK,
            "Scope with id $scopeId toggled to 'enabled' value '${scope.enabled}'"
        )
    } ?: StatusMessage(
        StatusCodes.CONFLICT,
        "Failed to toggle scope with id $scopeId: scope not found"
    )
}

internal suspend fun Plugin.dropScope(scopeId: String): StatusMessage {
    return state.scopeManager.deleteById(scopeId)?.let { scope ->
        cleanTopics(scope.id)
        handleChange(scope)
        StatusMessage(
            StatusCodes.OK,
            "Scope with id $scopeId was removed"
        )
    } ?: StatusMessage(
        StatusCodes.CONFLICT,
        "Failed to drop scope with id $scopeId: scope not found"
    )
}

private suspend fun Plugin.handleChange(scope: FinishedScope) {
    if (scope.buildVersion == buildVersion) {
        calculateAndSendBuildCoverage()
        updateOverlap()
        send(buildVersion, Routes.ActiveScope, activeScope.summary)
        calculateAndSendScopeCoverage()
    }
    sendScopes(scope.buildVersion)
    sendScopeSummary(scope.summary, scope.buildVersion)
}

private suspend fun Plugin.updateOverlap() {
    val coverContext = state.coverContext()
    val overlap = activeScope.flatten().overlappingBundle(coverContext).toCoverDto(coverContext.packageTree)
    activeScope.updateSummary { it.copy(coverage = it.coverage.copy(overlap = overlap)) }
}
