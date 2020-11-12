package com.epam.drill.plugins.test2code.api

import kotlinx.serialization.*

@Serializable
sealed class Action

@SerialName("START")
@Serializable
data class StartNewSession(val payload: StartPayload) : Action()

@SerialName("ADD_SESSION_DATA")
@Serializable
data class AddSessionData(val payload: SessionDataPayload) : Action()

@SerialName("ADD_COVERAGE")
@Serializable
data class AddCoverage(val payload: CoverPayload) : Action()

@SerialName("CANCEL")
@Serializable
data class CancelSession(val payload: SessionPayload) : Action()

@SerialName("CANCEL_ALL")
@Serializable
object CancelAllSessions : Action()

@SerialName("STOP")
@Serializable
data class StopSession(val payload: StopSessionPayload) : Action()

@SerialName("STOP_ALL")
@Serializable
object StopAllSessions : Action()

@SerialName("SWITCH_ACTIVE_SCOPE")
@Serializable
data class SwitchActiveScope(val payload: ActiveScopeChangePayload) : Action()

@SerialName("RENAME_SCOPE")
@Serializable
data class RenameScope(val payload: RenameScopePayload) : Action()

@SerialName("TOGGLE_SCOPE")
@Serializable
data class ToggleScope(val payload: ScopePayload) : Action()

@SerialName("DROP_SCOPE")
@Serializable
data class DropScope(val payload: ScopePayload) : Action()

@Serializable
@SerialName("UPDATE_SETTINGS")
data class UpdateSettings(val payload: List<Setting>) : Action()

@Serializable
@SerialName("TOGGLE_BASELINE")
object ToggleBaseline : Action()
