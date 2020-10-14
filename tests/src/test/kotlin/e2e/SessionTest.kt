package com.epam.drill.plugins.test2code.e2e


import com.epam.drill.builds.*
import com.epam.drill.e2e.*
import com.epam.drill.e2e.plugin.*
import com.epam.drill.plugins.test2code.*
import com.epam.drill.plugins.test2code.api.*
import com.epam.drill.plugins.test2code.common.api.*
import io.kotlintest.*
import io.ktor.http.*
import kotlinx.coroutines.*
import java.util.*
import kotlin.test.*

class SessionTest : E2EPluginTest() {


    @Test
    fun `e2e test session test`() {
        createSimpleAppWithPlugin<CoverageSocketStreams> {
            connectAgent<Build1> { plugUi, build ->

                plugUi.activeSessions()?.run {
                    count shouldBe 0
                    testTypes shouldBe emptySet()
                }

                val startNewSession = StartNewSession(StartPayload("MANUAL")).stringify()
                pluginAction(startNewSession) { status, content ->
                    status shouldBe HttpStatusCode.OK
                    val startSession = content!!.parseJsonData<StartAgentSession>()

                    plugUi.activeSessions()?.run { count shouldBe 1 }

                    runWithSession(startSession.payload.sessionId) {
                        val gt = build.entryPoint()
                        gt.test1()
                        gt.test2()
                        gt.test3()
                    }

                    pluginAction(StopSession(StopSessionPayload(startSession.payload.sessionId)).stringify()).join()
                }.join()
                delay(100)
            }.reconnect<Build2> { plugUi, _ ->
                plugUi.activeSessions()?.run {
                    count shouldBe 0
                    testTypes shouldBe emptySet()
                }

            }
        }
    }

    @Test
    fun `finish active scope without stopping session`() {
        createSimpleAppWithPlugin<CoverageSocketStreams> {
            connectAgent<Build1> { plugUi, build ->

                plugUi.activeSessions()!!.run {
                    count shouldBe 0
                    testTypes shouldBe emptySet()
                }

                plugUi.activeScope()!!.coverage.count.covered shouldBe 0
                plugUi.buildCoverage()!!.count.covered shouldBe 0

                "${UUID.randomUUID()}".let { sessionId ->
                    val startNewSession = StartNewSession(StartPayload("MANUAL", sessionId)).stringify()
                    pluginAction(startNewSession) { status, _ ->
                        status shouldBe HttpStatusCode.OK

                        plugUi.activeSessions()!!.run { count shouldBe 1 }

                        runWithSession(sessionId) {
                            val gt = build.entryPoint()
                            gt.test1()
                            gt.test2()
                        }
                    }.join()

                    val stopSession = StopSession(StopSessionPayload(sessionId)).stringify()
                    pluginAction(stopSession).join()
                    plugUi.activeSessions()!!.count shouldBe 0
                    plugUi.activeScope()!!.coverage.count shouldBe Count(11, 15)
                }

                "${UUID.randomUUID()}".let { sessionId ->
                    val startSession = StartNewSession(StartPayload("AUTO", sessionId)).stringify()
                    pluginAction(startSession) { status, _ ->
                        status shouldBe HttpStatusCode.OK

                        plugUi.activeSessions()!!.run { count shouldBe 1 }

                        runWithSession(sessionId) {
                            val gt = build.entryPoint()
                            gt.test3()
                        }
                    }.join()
                }
                val switchScope = SwitchActiveScope(
                    ActiveScopeChangePayload(
                        scopeName = "new scope 2",
                        savePrevScope = true,
                        prevScopeEnabled = true
                    )
                ).stringify()
                pluginAction(switchScope).join()

                plugUi.activeSessions()!!.count shouldBe 0
                plugUi.activeScope()!!.coverage.count.covered shouldBe 0
                plugUi.buildCoverage()!!.count shouldBe Count(11, 15)
            }
        }
    }

    @Test
    fun `start and finish 2 sessions`() {
        createSimpleAppWithPlugin<CoverageSocketStreams> {
            connectAgent<Build1> { plugUi, build ->
                plugUi.activeSessions()!!.run {
                    count shouldBe 0
                    testTypes shouldBe emptySet()
                }

                plugUi.activeScope()!!.coverage.count shouldBe Count(0, 15)
                plugUi.buildCoverage()!!.count shouldBe Count(0, 15)
                val startNewSession = StartNewSession(StartPayload("MANUAL")).stringify()
                pluginAction(startNewSession) { status, content ->
                    status shouldBe HttpStatusCode.OK
                    val startSession = content!!.parseJsonData<StartAgentSession>()
                    plugUi.activeSessions()!!.run { count shouldBe 1 }
                    runWithSession(startSession.payload.sessionId) {
                        val gt = build.entryPoint()
                        gt.test1()
                        gt.test2()
                    }

                    val startNewSession2 = StartNewSession(StartPayload("AUTO")).stringify()

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
                            testTypes shouldBe setOf("AUTO")
                        }

                        pluginAction(StopSession(StopSessionPayload(startSession2.payload.sessionId)).stringify()).join()
                    }.join()
                }.join()
                plugUi.activeSessions()!!.count shouldBe 0
                plugUi.activeScope()!!
                plugUi.activeScope()!!.coverage.run {
                    testTypeOverlap.count shouldBe Count(7, 15)
                    byTestType.size shouldBe 2
                }
            }
        }
    }
}
