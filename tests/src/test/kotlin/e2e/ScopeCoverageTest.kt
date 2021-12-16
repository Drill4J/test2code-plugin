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
package e2e

import com.epam.drill.builds.*
import com.epam.drill.e2e.*
import com.epam.drill.e2e.plugin.*
import com.epam.drill.plugins.test2code.*
import com.epam.drill.plugins.test2code.api.*
import com.epam.drill.plugins.test2code.api.TestResult
import com.epam.drill.plugins.test2code.common.api.*
import io.kotlintest.*
import io.kotlintest.matchers.doubles.*
import io.ktor.http.*
import kotlinx.coroutines.*
import java.util.*
import kotlin.test.*

class ScopeCoverageTest : E2EPluginTest() {

    private val testWithCoverageName = "TestWithCoverage"
    private val testWithoutCoverageName = "EmptyTest"
    private val testType = "AUTO"

    @Test
    fun `tests without coverage must be in scope when at least one test have coverage`() {
        createSimpleAppWithPlugin<CoverageSocketStreams>(timeout = 60L) {
            connectAgent<Build1> { plugUi, build ->
                val sessionId = "${UUID.randomUUID()}"
                val startNewSession = StartNewSession(StartPayload(testType, sessionId)).stringify()

                pluginAction(startNewSession) { status, _ ->
                    status shouldBe HttpStatusCode.OK
                }.join()

                runWithSession(sessionId, testWithCoverageName) {
                    build.entryPoint().test2()
                }

                val payloadWithEmptyTest = AddTests(
                    AddTestsPayload(
                        sessionId,
                        listOf(TestInfo("", testWithoutCoverageName, TestResult.PASSED, 0, 228))
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
                    coverage()!!.percentage shouldBeGreaterThan 46.6
                    val allTests = tests()!!
                    allTests.size shouldBe 2
                    allTests.forEach {
                        when (it.name) {
                            testWithCoverageName -> {
                                it.coverage.percentage shouldBeGreaterThan 46.6
                                it.type shouldBe testType
                                it.overview.result shouldBe TestResult.PASSED
                            }
                            testWithoutCoverageName -> {
                                it.coverage.percentage shouldBe 0.0
                                it.type shouldBe testType
                                it.overview.result shouldBe TestResult.PASSED
                            }
                            else -> fail("Unknown test in collection")
                        }
                    }
                }
            }
        }
    }

    @Test
    fun `tests without coverage shouldn't be in scope`() {
        createSimpleAppWithPlugin<CoverageSocketStreams>(timeout = 60L) {
            connectAgent<CustomBuild> { plugUi, _ ->
                val sessionId = "${UUID.randomUUID()}"
                val startNewSession = StartNewSession(StartPayload(testType, sessionId)).stringify()
                pluginAction(startNewSession) { status, _ ->
                    status shouldBe HttpStatusCode.OK
                }.join()

                val emptyTests = mutableListOf<TestInfo>()
                repeat(5) { index ->
                    emptyTests.add(
                        TestInfo(
                            "", "$testWithoutCoverageName$index", TestResult.PASSED, 0, 500
                        )
                    )
                }

                val payloadWithEmptyTest = AddTests(
                    AddTestsPayload(
                        sessionId,
                        emptyTests
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
                    coverage()!!.percentage shouldBe 0.0
                    scope()!!.coverage.apply {
                        byTestType.size shouldBe 0
                        methodCount shouldBe zeroCount
                        count shouldBe zeroCount
                        packageCount shouldBe zeroCount
                    }
                }
            }
        }
    }
}

