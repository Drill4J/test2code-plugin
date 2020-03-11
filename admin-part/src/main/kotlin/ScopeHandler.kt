package com.epam.drill.plugins.test2code

import com.epam.drill.plugin.api.message.*
import kotlinx.coroutines.*

internal fun Test2CodeAdminPart.initActiveScope() {
    val realtimeEnabled = System.getProperty("plugin.feature.drealtime")?.toBoolean() ?: true
    if (realtimeEnabled) {
        GlobalScope.launch {
            activeScope.subscribeOnChanges { sessions ->
                updateSummary { it.calculateCoverage(sessions, pluginInstanceState) }
                sendScopeMessages()
                val coverageInfoSet = sessions.calculateCoverageData(pluginInstanceState, buildVersion)
                coverageInfoSet.sendScopeCoverage(buildVersion, this.id)
            }
        }
    }
}

internal suspend fun Test2CodeAdminPart.changeActiveScope(scopeChange: ActiveScopeChangePayload): StatusMessage =
    if (pluginInstanceState.scopeNameNotExisting(scopeChange.scopeName, buildVersion)) {
        val prevScope = pluginInstanceState.changeActiveScope(scopeChange.scopeName.trim())
        if (scopeChange.savePrevScope) {
            if (prevScope.any()) {
                val finishedScope = prevScope.finish(scopeChange.prevScopeEnabled)
                sendScopeSummary(finishedScope.summary)
                println("$finishedScope has been saved.")
                pluginInstanceState.scopeManager.saveScope(finishedScope)
                if (finishedScope.enabled) {
                    calculateAndSendBuildAndChildrenCoverage()
                }
            } else {
                println("$prevScope is empty, it won't be added to the build.")
                cleanTopics(prevScope.id)
            }
        }
        initActiveScope()
        println("Current active scope $activeScope")
        sendActiveSessions()
        calculateAndSendScopeCoverage()
        sendScopeMessages()
        StatusMessage(StatusCodes.OK, "Switched to the new scope \'${scopeChange.scopeName}\'")
    } else StatusMessage(
        StatusCodes.CONFLICT,
        "Failed to switch to a new scope: name ${scopeChange.scopeName} is already in use"
    )


internal suspend fun Test2CodeAdminPart.renameScope(payload: RenameScopePayload): StatusMessage = when {
    pluginInstanceState.scopeNotExisting(payload.scopeId) ->
        StatusMessage(
            StatusCodes.NOT_FOUND,
            "Failed to rename scope with id ${payload.scopeId}: scope not found"
        )
    pluginInstanceState.scopeNameNotExisting(payload.scopeName, buildVersion) -> {
        pluginInstanceState.renameScope(payload.scopeId, payload.scopeName)
        val scope: Scope = pluginInstanceState.scopeManager.getScope(payload.scopeId) ?: activeScope
        sendScopeMessages(scope.buildVersion)
        sendScopeSummary(scope.summary, scope.buildVersion)
        StatusMessage(StatusCodes.OK, "Renamed scope with id ${payload.scopeId} -> ${payload.scopeName}")
    }
    else -> StatusMessage(
        StatusCodes.CONFLICT,
        "Scope with such name already exists. Please choose a different name."
    )
}

internal suspend fun Test2CodeAdminPart.toggleScope(scopeId: String): StatusMessage {
    pluginInstanceState.toggleScope(scopeId)
    return pluginInstanceState.scopeManager.getScope(scopeId)?.let { scope ->
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
    return pluginInstanceState.scopeManager.delete(scopeId)?.let { scope ->
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
