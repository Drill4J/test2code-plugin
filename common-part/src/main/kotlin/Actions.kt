package com.epam.drill.plugins.coverage

import kotlinx.serialization.*

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
data class ActiveScopeChangePayload(
    val scopeName: String,
    val savePrevScope: Boolean = false,
    val prevScopeEnabled: Boolean = true
)

@Serializable
data class RenameScopePayload(
    val scopeId: String,
    val scopeName: String
)

@Serializable
data class ScopePayload(val scopeId: String = "")
