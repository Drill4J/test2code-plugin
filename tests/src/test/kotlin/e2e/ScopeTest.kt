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
package com.epam.drill.plugins.test2code.e2e

import com.epam.drill.builds.*
import com.epam.drill.e2e.*
import com.epam.drill.e2e.plugin.*
import com.epam.drill.plugins.test2code.*
import com.epam.drill.plugins.test2code.api.*
import com.epam.drill.plugins.test2code.common.api.*
import e2e.*
import io.kotlintest.*
import io.ktor.http.*
import kotlinx.coroutines.*
import kotlin.test.*

class ScopeTest : E2EPluginTest() {

    @Test
    fun `e2e scope test`() {
        createSimpleAppWithPlugin<CoverageSocketStreams> {
            connectAgent<Build1> { plugUi, _ ->
                plugUi.activeScope()!!.apply {
                    name shouldBe "New Scope 1"
                    started shouldNotBe 0L
                    finished shouldBe 0L
                    coverage.percentage shouldBe 0.0
                    enabled shouldBe true
                    active shouldBe true
                }
                delay(100)
            }.reconnect<Build2> { plugUi, _ ->

                val activeScope = plugUi.activeScope()
                activeScope!!.apply {
                    name shouldBe "New Scope 1"
                    started shouldNotBe 0L
                    finished shouldBe 0L
                    coverage.percentage shouldBe 0.0
                    enabled shouldBe true
                    active shouldBe true
                }
                val renameScope =
                    RenameScope(RenameScopePayload(activeScope.id, "integration")).stringify()
                pluginAction(renameScope).join()
                plugUi.activeScope()!!.name shouldBe "integration"

                val switchActiveScopeWrong = SwitchActiveScope(ActiveScopeChangePayload("integration")).stringify()
                pluginAction(switchActiveScopeWrong) { status, _ ->
                    status shouldBe HttpStatusCode.Conflict
                }.join()
                val switchActiveScope = SwitchActiveScope(ActiveScopeChangePayload("scope integration")).stringify()
                pluginAction(switchActiveScope) { status, _ ->
                    status shouldBe HttpStatusCode.OK
                }.join()
                plugUi.activeScope()!!.name shouldBe "scope integration"
            }

        }
    }

    @Test
    fun `finish active scope and drop it after that`() {
        lateinit var droppedScopeId: String
        createSimpleAppWithPlugin<CoverageSocketStreams> {
            connectAgent<Build1> { plugUi, build ->
                plugUi.buildCoverage()!!.percentage shouldBe 0.0
                plugUi.activeSessions()!!.run {
                    count shouldBe 0
                    testTypes shouldBe emptySet()
                }
                plugUi.activeScope()!!.apply {
                    coverage.percentage shouldBe 0.0
                    droppedScopeId = id
                }
                val startNewSession = StartNewSession(StartPayload(MANUAL_TEST_TYPE)).stringify()
                pluginAction(startNewSession) { status, content ->
                    status shouldBe HttpStatusCode.OK
                    val startSession = content!!.parseJsonData<StartAgentSession>()
                    plugUi.activeSessions()!!.run { count shouldBe 1 }
                    runWithSession(startSession.payload.sessionId) {
                        val gt = build.entryPoint()
                        gt.test1()
                        gt.test2()
                        gt.test3()
                    }
                    pluginAction(StopAgentSession(AgentSessionPayload(startSession.payload.sessionId)).stringify()).join()
                }.join()
                plugUi.activeSessions()!!.count shouldBe 0
                plugUi.activeScope()!!.coverage.percentage shouldBe 100.0
                val switchScope = SwitchActiveScope(
                    ActiveScopeChangePayload(
                        scopeName = "new2",
                        savePrevScope = true,
                        prevScopeEnabled = true
                    )
                ).stringify()
                pluginAction(switchScope).join()
                plugUi.buildCoverage()!!.percentage shouldBe 100.0
                pluginAction(DropScope(ScopePayload(droppedScopeId)).stringify()).join()
                plugUi.buildCoverage()!!.percentage shouldBe 0.0
                plugUi.activeScope()!!.id shouldNotBe droppedScopeId
                plugUi.scopes() shouldBe null
            }
        }
    }

    @Test
    fun `finish active scope with ignoring and reignore after that`() {
        createSimpleAppWithPlugin<CoverageSocketStreams> {
            connectAgent<Build1> { plugUi, build ->
                plugUi.activeSessions()!!.run {
                    count shouldBe 0
                    testTypes shouldBe emptySet()
                }
                val ignoredScopeId = plugUi.activeScope()!!.apply {
                    coverage.percentage shouldBe 0.0
                }.id
                val startNewSession = StartNewSession(StartPayload(MANUAL_TEST_TYPE)).stringify()
                pluginAction(startNewSession) { status, content ->
                    status shouldBe HttpStatusCode.OK
                    val startSession = content!!.parseJsonData<StartAgentSession>()
                    plugUi.activeSessions()!!.run { count shouldBe 1 }
                    runWithSession(startSession.payload.sessionId) {
                        val gt = build.entryPoint()
                        gt.test1()
                        gt.test2()
                        gt.test3()
                    }
                    pluginAction(StopAgentSession(AgentSessionPayload(startSession.payload.sessionId)).stringify()).join()
                }.join()
                plugUi.activeSessions()!!.count shouldBe 0
                plugUi.activeScope()!!.coverage.percentage shouldBe 100.0
                val switchScope = SwitchActiveScope(
                    ActiveScopeChangePayload(
                        scopeName = "new2",
                        savePrevScope = true,
                        prevScopeEnabled = true
                    )
                ).stringify()
                pluginAction(switchScope).join()
                plugUi.buildCoverage()!!.percentage shouldBe 0.0
                pluginAction(ToggleScope(ScopePayload(ignoredScopeId)).stringify()).join()
                plugUi.buildCoverage()!!.percentage shouldBe 100.0
            }
        }
    }
}
