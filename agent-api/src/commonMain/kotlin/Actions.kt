/**
 * Copyright 2020 - 2022 EPAM Systems
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.epam.drill.plugins.test2code.common.api

import kotlinx.serialization.*

@Serializable
sealed class AgentAction

@Serializable
@SerialName("START_AGENT_SESSION")
data class StartAgentSession(val payload: StartSessionPayload) : AgentAction()

@Serializable
@SerialName("ADD_SESSION_DATA")
data class AddAgentSessionData(val payload: AgentSessionDataPayload) : AgentAction()

@Serializable
@SerialName("ADD_SESSION_TESTS")
data class AddAgentSessionTests(val payload: AgentSessionTestsPayload) : AgentAction()

@SerialName("STOP")
@Serializable
data class StopAgentSession(val payload: AgentSessionPayload) : AgentAction()

@SerialName("STOP_ALL")
@Serializable
object StopAllAgentSessions : AgentAction()

@SerialName("CANCEL")
@Serializable
data class CancelAgentSession(val payload: AgentSessionPayload) : AgentAction()

@SerialName("CANCEL_ALL")
@Serializable
object CancelAllAgentSessions : AgentAction()

@SerialName("SYNC_MESSAGE")
@Serializable
data class SyncMessage(val sessions: Set<StartSessionPayload>) : AgentAction()
