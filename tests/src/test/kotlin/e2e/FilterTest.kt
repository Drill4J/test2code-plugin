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
import io.ktor.http.*
import kotlin.test.*
import kotlin.test.Test

class FilterTest : E2EPluginTest() {

    /**
     * @see SessionTest start and finish 2 sessions
     */
    @Ignore
    @Test
    fun `start and finish 2 sessions`() {
        createSimpleAppWithPlugin<CoverageSocketStreams> {
            connectAgent<Build1> { plugUi, build ->
                plugUi.activeScope()!!.coverage.count shouldBe Count(0, 15)
                plugUi.buildCoverage()!!.count shouldBe Count(0, 15)
                val startNewSession = StartNewSession(StartPayload(MANUAL_TEST_TYPE, testName = "first")).stringify()
                pluginAction(startNewSession) { status, content ->
                    status shouldBe HttpStatusCode.OK
                    val startSession = content!!.parseJsonData<StartAgentSession>()
                    plugUi.activeSessions()!!.run { count shouldBe 1 }
                    runWithSession(startSession.payload.sessionId) {
                        val gt = build.entryPoint()
                        gt.test1()
                        gt.test2()
                    }

                    val startNewSession2 = StartNewSession(StartPayload(MANUAL_TEST_TYPE, testName = "second")).stringify()

                    pluginAction(startNewSession2) { status2, content2 ->
                        status2 shouldBe HttpStatusCode.OK

                        val startSession2 = content2!!.parseJsonData<StartAgentSession>()
                        plugUi.activeSessions()!!.run { count shouldBe 2 }
                        runWithSession(startSession2.payload.sessionId) {
                            val gt = build.entryPoint()
                            gt.test2()
                            gt.test3()
                        }

                        pluginAction(StopAgentSession(AgentSessionPayload(startSession.payload.sessionId)).stringify()).join()

                        plugUi.activeSessions()!!.apply {
                            count shouldBe 1
                            testTypes shouldBe setOf(MANUAL_TEST_TYPE)
                        }
                        pluginAction(StopAgentSession(AgentSessionPayload(startSession2.payload.sessionId)).stringify()).join()

//                        pluginAction(StopSession(StopSessionPayload(startSession2.payload.sessionId)).stringify()).join()
                    }.join()
                }.join()
                //todo 1
                plugUi.activeSessions()!!.count shouldBe 0
                plugUi.activeScope()!!
                plugUi.activeScope()!!.coverage.run {
                    println(percentage)
                    println(this)
                    testTypeOverlap.count shouldBe Count(7, 15).toDto()
                    byTestType.size shouldBe 1
                }
                //todo create filter action
                //todo get a new values from topics
            }
        }
    }
}
