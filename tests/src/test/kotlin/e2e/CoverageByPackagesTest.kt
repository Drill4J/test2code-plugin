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

import com.epam.drill.builds.Build1
import com.epam.drill.e2e.E2EPluginTest
import com.epam.drill.e2e.plugin.runWithSession
import com.epam.drill.plugins.test2code.CoverageSocketStreams
import com.epam.drill.plugins.test2code.api.*
import com.epam.drill.plugins.test2code.common.api.AgentSessionPayload
import com.epam.drill.plugins.test2code.common.api.StartAgentSession
import com.epam.drill.plugins.test2code.common.api.StopAgentSession
import com.epam.drill.plugins.test2code.coverage.TestKey
import com.epam.drill.plugins.test2code.coverage.id
import com.epam.drill.plugins.test2code.parseJsonData
import com.epam.drill.plugins.test2code.stringify
import com.epam.drill.plugins.test2code.util.crc64
import e2e.MANUAL_TEST_TYPE
import io.kotlintest.matchers.doubles.shouldBeGreaterThan
import io.kotlintest.shouldBe
import io.ktor.http.*
import kotlinx.coroutines.delay
import kotlin.test.Ignore
import kotlin.test.Test

class CoverageByPackagesTest : E2EPluginTest() {

    private val testDetails = TestDetails(testName = "xxxx")
    private val testHash = testDetails.testName.crc64

    @Test
    @Ignore
    fun `cover one method in 2 scopes`() {
        createSimpleAppWithPlugin<CoverageSocketStreams>(timeout = 60L) {
            connectAgent<Build1> { plugUi, build ->
                plugUi.coveragePackages()!!.first().apply {
                    id shouldBe "vsu9sbxes5bl"
                    coveredClassesCount shouldBe 0
                    name shouldBe "com/epam/test"
                    coverage shouldBe 0.0
                    totalClassesCount shouldBe 1
                    assocTestsCount shouldBe 0
                }
                val startNewSession = StartNewSession(StartPayload(MANUAL_TEST_TYPE)).stringify()
                lateinit var cont: String
                pluginAction(startNewSession) { status, content ->
                    status shouldBe HttpStatusCode.OK
                    cont = content!!
                }.join()
                val startSession = cont.parseJsonData<StartAgentSession>()
                println(startSession)
                runWithSession(startSession.payload.sessionId, testHash = testHash) {
                    val gt = build.entryPoint()
                    gt.test1()
                }


                pluginAction(StopAgentSession(AgentSessionPayload(startSession.payload.sessionId)).stringify()) { status, _ ->
                    status shouldBe HttpStatusCode.OK
                }.join()
                delay(1100)//todo move it to core library

                val scopeId = plugUi.activeScope()!!.id
                plugUi.subscribeOnScope(scopeId) {
                    coveragePackages()!!.first().apply {
                        id shouldBe "vsu9sbxes5bl"
                        coveredClassesCount shouldBe 1
                        name shouldBe "com/epam/test"
                        coverage shouldBeGreaterThan 46.6
                        totalClassesCount shouldBe 1
                        classes shouldBe emptyList()
                        assocTestsCount shouldBe 1
                    }
                    plugUi.subscribeOnTest(
                        scopeId, TestKey(type = MANUAL_TEST_TYPE, id = testHash)
                            .id()
                    ) {
                        methodsCoveredByTest()!!.apply {
                            details.testName shouldBe testDetails.testName
                            methodCounts.apply {
                                new shouldBe 0
                                modified shouldBe 0
                                unaffected shouldBe 0
                                all shouldBe 2
                            }
                        }
                    }
                }
                val switchScope = SwitchActiveScope(
                    ActiveScopeChangePayload(
                        scopeName = "new2",
                        savePrevScope = true,
                        prevScopeEnabled = true
                    )
                ).stringify()
                pluginAction(switchScope)
                plugUi.coveragePackages()!!
                plugUi.coveragePackages()!!.first().apply {
                    id shouldBe "vsu9sbxes5bl"
                    coveredClassesCount shouldBe 1
                    name shouldBe "com/epam/test"
                    coverage shouldBeGreaterThan 46.6
                    totalClassesCount shouldBe 1
                    assocTestsCount shouldBe 1
                }

                val startNewSession2 = StartNewSession(StartPayload(MANUAL_TEST_TYPE)).stringify()
                pluginAction(startNewSession2) { status, content ->
                    status shouldBe HttpStatusCode.OK
                    val startSession2 = content!!.parseJsonData<StartAgentSession>()
                    runWithSession(startSession2.payload.sessionId) {
                        val gt = build.entryPoint()
                        gt.test1()
                    }

                    pluginAction(StopAgentSession(AgentSessionPayload(startSession2.payload.sessionId)).stringify()) { st, _ ->
                        st shouldBe HttpStatusCode.OK
                    }.join()
                }.join()
                delay(1100)//todo move it to core library

                plugUi.subscribeOnScope(plugUi.activeScope()!!.id) {
                    coveragePackages()!!.first().apply {
                        id shouldBe "vsu9sbxes5bl"
                        coveredClassesCount shouldBe 1
                        name shouldBe "com/epam/test"
                        coverage shouldBeGreaterThan 46.6
                        totalClassesCount shouldBe 1
                        assocTestsCount shouldBe 1
                    }
                }
                val switchScope2 = SwitchActiveScope(
                    ActiveScopeChangePayload(
                        scopeName = "new3",
                        savePrevScope = true,
                        prevScopeEnabled = true
                    )
                ).stringify()
                pluginAction(switchScope2)
                plugUi.coveragePackages()!!.first().apply {
                    id shouldBe "vsu9sbxes5bl"
                    coveredClassesCount shouldBe 1
                    name shouldBe "com/epam/test"
                    coverage shouldBeGreaterThan 46.6
                    totalClassesCount shouldBe 1
                    assocTestsCount shouldBe 1
                }
            }
        }
    }
}
