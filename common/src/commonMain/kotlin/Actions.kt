package com.epam.drill.plugins.test2code

import kotlinx.serialization.*

@Polymorphic
@Serializable
abstract class Action

@SerialName("START_AGENT_SESSION")
@Serializable
data class StartSession(val payload: StartSessionPayload) : Action()

@SerialName("STOP")
@Serializable
data class StopSession(val payload: SessionPayload) : Action()

@SerialName("CANCEL")
@Serializable
data class CancelSession(val payload: SessionPayload) : Action()
