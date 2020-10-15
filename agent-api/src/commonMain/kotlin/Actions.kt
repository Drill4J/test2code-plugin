package com.epam.drill.plugins.test2code.common.api

import kotlinx.serialization.*

@Serializable
sealed class AgentAction

@Serializable
@SerialName("INIT_ACTIVE_SCOPE")
data class InitActiveScope(val payload: InitScopePayload) : AgentAction()

@Serializable
@SerialName("START_AGENT_SESSION")
data class StartAgentSession(val payload: StartSessionPayload) : AgentAction()

@Serializable
@SerialName("ADD_SESSION_DATA")
data class AddAgentSessionData(val payload: AgentSessionDataPayload) : AgentAction()

@SerialName("STOP")
@Serializable
data class StopAgentSession(val payload: AgentSessionPayload) : AgentAction()

@SerialName("CANCEL")
@Serializable
data class CancelAgentSession(val payload: AgentSessionPayload) : AgentAction()

@Serializable
@SerialName("CANCEL_ALL")
object CancelAllAgentSessions : AgentAction()
