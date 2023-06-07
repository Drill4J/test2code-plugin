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
import com.epam.drill.plugins.test2code.api.TestResult
import com.epam.drill.plugins.test2code.common.api.*
import com.epam.drill.plugins.test2code.util.*
import io.kotlintest.*
import io.kotlintest.matchers.doubles.*
import io.ktor.http.*
import kotlinx.coroutines.*
import java.util.*
import kotlin.test.*


class TestNameMappingTest : E2EPluginTest() {

    private val testName = "test"
    private val sessionId = "${UUID.randomUUID()}"
    private val labels = setOf(Label("Session", sessionId))
    private val manualTest = TestDetails(testName = testName, labels = labels)
    private val details = TestDetails(
        engine = "testng",
        path = "class",
        testName = testName,
        params = mapOf("one" to "two"),
        labels = labels
    )

    // Any hash algorithm can be used
    private val testHash = details.toString().crc64

    @Test
    fun `mapping test name on test hash`() {
        createSimpleAppWithPlugin<CoverageSocketStreams> {
            connectAgent<Build1> { plugUi, build ->

                val startNewSession = StartNewSession(StartPayload(AUTO_TEST_TYPE, sessionId)).stringify()

                pluginAction(startNewSession) { status, _ ->
                    status shouldBe HttpStatusCode.OK
                }.join()

                runWithSession(sessionId, testHash = testHash) {
                    build.entryPoint().test2()
                }

                val payloadWithEmptyTest = AddTests(
                    AddTestsPayload(
                        sessionId, listOf(TestInfo(
                            id = testHash,
                            result = TestResult.PASSED,
                            startedAt = 0,
                            finishedAt = 228,
                            details = details
                        ))
                    )
                ).stringify()

                pluginAction(payloadWithEmptyTest) { status, _ ->
                    status shouldBe HttpStatusCode.OK
                }.join()

                pluginAction(StopAgentSession(AgentSessionPayload(sessionId)).stringify()) { status, _ ->
                    status shouldBe HttpStatusCode.OK
                }.join()

                delay(1000)
                val scopeId = plugUi.activeScope()!!.id
                plugUi.subscribeOnScope(scopeId) {
                    val allTests = tests()!!
                    allTests.size shouldBe 1
                    allTests[0].overview.details shouldBe details
                }
            }
        }
    }

    @Test
    fun `probe without test hash mapping should be displaced with default test name`() {
        createSimpleAppWithPlugin<CoverageSocketStreams> {
            connectAgent<Build1> { plugUi, build ->

                val startNewSession = StartNewSession(StartPayload(AUTO_TEST_TYPE, sessionId)).stringify()

                pluginAction(startNewSession) { status, _ ->
                    status shouldBe HttpStatusCode.OK
                }.join()

                runWithSession(sessionId, testName = testName) {
                    build.entryPoint().test2()
                }

                pluginAction(StopAgentSession(AgentSessionPayload(sessionId)).stringify()) { status, _ ->
                    status shouldBe HttpStatusCode.OK
                }.join()

                delay(1000)
                val scopeId = plugUi.activeScope()!!.id
                plugUi.subscribeOnScope(scopeId) {
                    val allTests = tests()!!
                    allTests.size shouldBe 1
                    allTests[0].overview.details shouldBe manualTest
                }
            }
        }
    }

    @Test
    fun `probes with with hash and without should display correctly`() {

        createSimpleAppWithPlugin<CoverageSocketStreams> {
            connectAgent<Build1> { plugUi, build ->

                val startNewSession = StartNewSession(StartPayload(AUTO_TEST_TYPE, sessionId)).stringify()

                pluginAction(startNewSession) { status, _ ->
                    status shouldBe HttpStatusCode.OK
                }.join()

                runWithSession(sessionId, testHash = testHash) {
                    build.entryPoint().test2()
                }

                runWithSession(sessionId, testName = testName) {
                    build.entryPoint().test2()
                }

                val payloadWithEmptyTest = AddTests(
                    AddTestsPayload(
                        sessionId, listOf(TestInfo(
                            id = testHash,
                            result = TestResult.PASSED,
                            startedAt = 0,
                            finishedAt = 228,
                            details = details
                        ))
                    )
                ).stringify()

                pluginAction(payloadWithEmptyTest) { status, _ ->
                    status shouldBe HttpStatusCode.OK
                }.join()

                pluginAction(StopAgentSession(AgentSessionPayload(sessionId)).stringify()) { status, _ ->
                    status shouldBe HttpStatusCode.OK
                }.join()

                delay(1000)
                val scopeId = plugUi.activeScope()!!.id
                plugUi.subscribeOnScope(scopeId) {
                    val allTests = tests()!!
                    allTests.size shouldBe 2
                    allTests.forEach {
                        when (it.overview.details) {
                            details -> {
                                it.coverage.percentage shouldBeGreaterThan 46.6
                                it.type shouldBe AUTO_TEST_TYPE
                                it.overview.result shouldBe TestResult.PASSED
                            }
                            manualTest -> {
                                it.coverage.percentage shouldBeGreaterThan 46.6
                                it.type shouldBe AUTO_TEST_TYPE
                                it.overview.result shouldBe TestResult.PASSED
                            }
                            else -> fail("Unknown test in collection")
                        }
                    }
                }
            }
        }
    }

}
