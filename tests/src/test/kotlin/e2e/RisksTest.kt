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
package com.epam.drill.plugins.test2code.e2e


import com.epam.drill.builds.*
import com.epam.drill.e2e.*
import com.epam.drill.e2e.plugin.*
import com.epam.drill.plugins.test2code.*
import com.epam.drill.plugins.test2code.api.*
import com.epam.drill.plugins.test2code.common.api.*
import e2e.*
import io.kotlintest.*
import io.kotlintest.matchers.numerics.*
import io.ktor.http.*
import kotlinx.coroutines.*
import kotlin.test.*


class RisksTest : E2EPluginTest() {

    @Ignore //TODO Fix tests EPMDJ-10319
    @Test
    fun `cover all risks during 2 builds`() {
        createSimpleAppWithPlugin<CoverageSocketStreams> {
            connectAgent<Build1> { plugUi, build ->

                plugUi.buildCoverage()!!.count.covered shouldBe 0
                plugUi.activeScope()!!.coverage.count.covered shouldBe 0

                val startNewSession = StartNewSession(StartPayload(MANUAL_TEST_TYPE)).stringify()
                pluginAction(startNewSession) { status, content ->
                    status shouldBe HttpStatusCode.OK
                    val startSession = content!!.parseJsonData<StartAgentSession>()

                    runWithSession(startSession.payload.sessionId) {
                        val gt = build.entryPoint()
                        gt.test1()
                        gt.test2()
                        gt.test3()
                    }
                    pluginAction(StopAgentSession(AgentSessionPayload(startSession.payload.sessionId)).stringify())
                }.join()
                plugUi.activeScope()!!.coverage.count.covered shouldNotBe 0

                val switchScope = SwitchActiveScope(
                    ActiveScopeChangePayload(
                        scopeName = "new2",
                        savePrevScope = true,
                        prevScopeEnabled = true
                    )
                ).stringify()

                pluginAction(switchScope) { status, _ ->
                    status shouldBe HttpStatusCode.OK
                }.join()
                plugUi.activeScope()!!.coverage.count.covered shouldBe 0
                plugUi.buildCoverage()!!.count.covered shouldBeGreaterThan 0
                delay(500)
            }.reconnect<Build2> { plugUi, build ->
                plugUi.risks()!!.apply {
                    size shouldBe 4
                    first { it.type == RiskType.NEW }.apply {
                        name shouldBe "firstMethod"
                    }
                    first { it.type == RiskType.MODIFIED }.apply {
                        name shouldBe "test1"
                    }
                }

                val startNewSession = StartNewSession(StartPayload(MANUAL_TEST_TYPE)).stringify()
                pluginAction(startNewSession) { status, content ->
                    status shouldBe HttpStatusCode.OK
                    val startSession = content!!.parseJsonData<StartAgentSession>()

                    runWithSession(startSession.payload.sessionId) {
                        val gt = build.entryPoint()
                        gt.test1()
                        gt.test2()
                        gt.test3()
                    }

                    delay(300)//todo move it to core library
                    val stopSession = StopAgentSession(AgentSessionPayload(startSession.payload.sessionId)).stringify()
                    pluginAction(stopSession) { status1, _ ->
                        status1 shouldBe HttpStatusCode.OK
                    }.join()
                }.join()

                val switchScope = SwitchActiveScope(
                    ActiveScopeChangePayload(
                        scopeName = "new2",
                        savePrevScope = true,
                        prevScopeEnabled = true
                    )
                ).stringify()
                pluginAction(switchScope) { status, _ ->
                    status shouldBe HttpStatusCode.OK
                }.join()
            }
        }
    }
}
