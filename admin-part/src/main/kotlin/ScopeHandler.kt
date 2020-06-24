package com.epam.drill.plugins.test2code

import com.epam.drill.plugin.api.message.*
import com.epam.drill.plugins.test2code.api.*
import com.epam.drill.plugins.test2code.common.api.*
import mu.*

private val logger = KotlinLogging.logger {}

internal fun Test2CodeAdminPart.initActiveScope() {
    val realtimeEnabled = System.getProperty("plugin.feature.drealtime")?.toBoolean() ?: true
    if (realtimeEnabled) {
        activeScope.subscribeOnChanges { sessions ->
            val context = pluginInstanceState.coverContext()
            updateSummary { it.calculateCoverage(sessions, context) }
            sendScopeMessages()
            val coverageInfoSet = sessions.calculateCoverageData(context)
            coverageInfoSet.sendScopeCoverage(buildVersion, this.id)
        }
    }
}

internal suspend fun Test2CodeAdminPart.changeActiveScope(
    scopeChange: ActiveScopeChangePayload
): Any = if (pluginInstanceState.scopeByName(scopeChange.scopeName) == null) {
    val prevScope = pluginInstanceState.changeActiveScope(scopeChange.scopeName.trim())
    pluginInstanceState.storeScopeCounter()
    sendActiveSessions()
    sendActiveScope()
    if (scopeChange.savePrevScope) {
        if (prevScope.any()) {
            val finishedScope = prevScope.finish(scopeChange.prevScopeEnabled)
            pluginInstanceState.scopeManager.store(finishedScope)
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

internal suspend fun Test2CodeAdminPart.scopeInitialized(prevId: String) {
    initActiveScope()
    val prevScope = pluginInstanceState.scopeManager.byId(prevId)
    prevScope?.apply {
        sendScopeSummary(summary)
    } ?: cleanTopics(prevId)
    sendScopes()
    calculateAndSendScopeCoverage()
    prevScope?.takeIf { it.enabled }.apply {
        calculateAndSendBuildAndChildrenCoverage()
    }
    logger.info { "Current active scope - $activeScope" }
}

internal suspend fun Test2CodeAdminPart.renameScope(
    payload: RenameScopePayload
): StatusMessage = pluginInstanceState.scopeById(payload.scopeId)?.let { scope ->
    val scopeName = payload.scopeName
    if (scope.summary.name != scopeName && pluginInstanceState.scopeByName(scopeName) == null) {
        pluginInstanceState.renameScope(payload.scopeId, scopeName)
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

internal suspend fun Test2CodeAdminPart.toggleScope(scopeId: String): StatusMessage {
    pluginInstanceState.toggleScope(scopeId)
    return pluginInstanceState.scopeManager.byId(scopeId)?.let { scope ->
        sendScopes(scope.buildVersion)
        sendScopeSummary(scope.summary, scope.buildVersion)
        calculateAndSendBuildAndChildrenCoverage(scope.buildVersion)
        StatusMessage(
            StatusCodes.OK,
            "Scope with id $scopeId toggled to 'enabled' value '${scope.enabled}'"
        )
    } ?: StatusMessage(
        StatusCodes.CONFLICT,
        "Failed to toggle scope with id $scopeId: scope not found"
    )
}

internal suspend fun Test2CodeAdminPart.dropScope(scopeId: String): StatusMessage {
    return pluginInstanceState.scopeManager.deleteById(scopeId)?.let { scope ->
        cleanTopics(scope.id)
        sendScopes(scope.buildVersion)
        sendScopeSummary(scope.summary, scope.buildVersion)
        calculateAndSendBuildAndChildrenCoverage(scope.buildVersion)
        StatusMessage(
            StatusCodes.OK,
            "Scope with id $scopeId was removed"
        )
    } ?: StatusMessage(
        StatusCodes.CONFLICT,
        "Failed to drop scope with id $scopeId: scope not found"
    )
}
