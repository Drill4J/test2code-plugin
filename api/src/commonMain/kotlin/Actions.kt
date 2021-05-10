/**
 * Copyright 2020 EPAM Systems
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

@SerialName("EXPORT_COVERAGE")
@Serializable
data class ExportCoverage(val payload: BuildPayload) : Action()

@SerialName("CANCEL")
@Serializable
data class CancelSession(val payload: SessionPayload) : Action()

@SerialName("CANCEL_ALL")
@Serializable
object CancelAllSessions : Action()

@SerialName("STOP")
@Serializable
data class StopSession(val payload: StopSessionPayload) : Action()

@SerialName("ADD_TESTS")
@Serializable
data class AddTests(val payload: AddTestsPayload) : Action()

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

@SerialName("REMOVE_BUILD")
@Serializable
data class RemoveBuild(val payload: BuildPayload) : Action()

@Serializable
@SerialName("UPDATE_SETTINGS")
data class UpdateSettings(val payload: List<Setting>) : Action()

@Serializable
@SerialName("TOGGLE_BASELINE")
object ToggleBaseline : Action()
