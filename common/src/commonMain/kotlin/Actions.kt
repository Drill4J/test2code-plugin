package com.epam.drill.plugins.test2code.common.api

import kotlinx.serialization.*

@Polymorphic
@Serializable
abstract class Action

@Serializable
@SerialName("INIT_ACTIVE_SCOPE")
data class InitActiveScope(val payload: InitScopePayload) : Action()

@Serializable
@SerialName("START_AGENT_SESSION")
data class StartSession(val payload: StartSessionPayload) : Action()

@SerialName("STOP")
@Serializable
data class StopSession(val payload: SessionPayload) : Action()

@SerialName("CANCEL")
@Serializable
data class CancelSession(val payload: SessionPayload) : Action()
