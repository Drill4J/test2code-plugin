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
import e2e.*
import io.kotlintest.*
import io.ktor.http.*
import kotlinx.coroutines.*
import java.util.*
import kotlin.test.*

class CoverageTest : E2EPluginTest() {

    @Test
    fun `e2e coverage test`() {
        createSimpleAppWithPlugin<CoverageSocketStreams> {
            connectAgent<Build1> { plugUi, _ ->
                plugUi.buildCoverage()!!.apply {
                    percentage shouldBe 0.0
                    byTestType shouldBe emptyList()
                }
                delay(100)
            }.reconnect<Build2> { plugUi, _ ->
                plugUi.buildCoverage()!!.apply {
                    percentage shouldBe 0.0
                    byTestType shouldBe emptyList()
                }
            }
        }
    }

    @Test
    @Ignore
    fun `coverage should not float when a test with the same name is called twice`() {
        createSimpleAppWithPlugin<CoverageSocketStreams> {
            connectAgent<Build1> { plugUi, build ->

                plugUi.activeSessions()!!.run {
                    count shouldBe 0
                    testTypes shouldBe emptySet()
                }

                plugUi.activeScope()!!.coverage.count.covered shouldBe 0
                plugUi.buildCoverage()!!.count.covered shouldBe 0

                "${UUID.randomUUID()}".let { sessionId ->
                    val startNewSession = StartNewSession(StartPayload(MANUAL_TEST_TYPE, sessionId)).stringify()
                    pluginAction(startNewSession) { status, _ ->
                        status shouldBe HttpStatusCode.OK

                        plugUi.activeSessions()!!.run { count shouldBe 1 }

                        runWithSession(sessionId) {
                            val gt = build.entryPoint()
                            gt.test1()
                            gt.test2()
                        }
                        runWithSession(sessionId) {
                            val gt = build.entryPoint()
                            gt.test1()
                        }
                    }.join()

                    val stopSession = StopSession(StopSessionPayload(sessionId)).stringify()
                    pluginAction(stopSession).join()
                    plugUi.activeSessions()!!.count shouldBe 0
                    plugUi.activeScope()!!.coverage.count shouldBe Count(11, 15)
                }
            }
        }
    }
}
