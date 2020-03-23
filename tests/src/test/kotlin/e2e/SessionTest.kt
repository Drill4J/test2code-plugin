package com.epam.drill.plugins.test2code.e2e


import com.epam.drill.builds.*
import com.epam.drill.e2e.*
import com.epam.drill.e2e.plugin.*
import com.epam.drill.plugins.test2code.*
import com.epam.drill.plugins.test2code.api.*
import com.epam.drill.plugins.test2code.common.api.*
import io.kotlintest.*
import io.ktor.http.*
import org.junit.jupiter.api.*

class SessionTest : E2EPluginTest() {


    @RepeatedTest(2)
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
                    val startSession = content!!.parseJsonData<StartSession>()

                    plugUi.activeSessions()?.run { count shouldBe 1 }

                    runWithSession(startSession.payload.sessionId) {
                        val gt = build.entryPoint()
                        gt.test1()
                        gt.test2()
                        gt.test3()
                    }

                    pluginAction(StopSession(SessionPayload(startSession.payload.sessionId)).stringify()).join()
                }.join()

            }.reconnect<Build2> { plugUi, _ ->
                plugUi.activeSessions()?.run {
                    count shouldBe 0
                    testTypes shouldBe emptySet()
                }

            }
        }
    }

    @Test
    fun `Finish active scope without stopping session`() {
        createSimpleAppWithPlugin<CoverageSocketStreams> {
            connectAgent<Build1> { plugUi, build ->

                plugUi.activeSessions()!!.run {
                    count shouldBe 0
                    testTypes shouldBe emptySet()
                }

                plugUi.activeScope()!!.coverage.ratio shouldBe 0.0
                plugUi.buildCoverage()!!.ratio shouldBe 0.0

                val startNewSession = StartNewSession(StartPayload("MANUAL")).stringify()
                pluginAction(startNewSession) { status, content ->
                    status shouldBe HttpStatusCode.OK
                    val startSession = content!!.parseJsonData<StartSession>()

                    plugUi.activeSessions()!!.run { count shouldBe 1 }

                    runWithSession(startSession.payload.sessionId) {
                        val gt = build.entryPoint()
                        gt.test1()
                        gt.test2()
                    }

                    pluginAction(StopSession(SessionPayload(startSession.payload.sessionId)).stringify()).join()
                }.join()
                plugUi.activeSessions()!!.count shouldBe 0
                plugUi.activeScope()!!.coverage.ratio shouldBe 73.33333333333333

                val startNewSession2 = StartNewSession(StartPayload("AUTO")).stringify()
                pluginAction(startNewSession2) { status, content ->
                    status shouldBe HttpStatusCode.OK
                    val startSession2 = content!!.parseJsonData<StartSession>()

                    plugUi.activeSessions()!!.run { count shouldBe 1 }

                    runWithSession(startSession2.payload.sessionId) {
                        val gt = build.entryPoint()
                        gt.test3()
                    }

                    val switchScope = SwitchActiveScope(
                        ActiveScopeChangePayload(
                            scopeName = "new scope 2",
                            savePrevScope = true,
                            prevScopeEnabled = true
                        )
                    ).stringify()
                    pluginAction(switchScope).join()
                }.join()
                plugUi.activeSessions()!!.count shouldBe 0
                plugUi.activeScope()!!.coverage.ratio shouldBe 0.0
                plugUi.buildCoverage()!!.ratio shouldBe 73.33333333333333
            }
        }
    }

    @Test
    fun `start and finish 2 session`() {
        createSimpleAppWithPlugin<CoverageSocketStreams> {
            connectAgent<Build1> { plugUi, build ->
                plugUi.activeSessions()!!.run {
                    count shouldBe 0
                    testTypes shouldBe emptySet()
                }

                plugUi.activeScope()!!.coverage.ratio shouldBe 0.0
                plugUi.buildCoverage()!!.ratio shouldBe 0.0
                val startNewSession = StartNewSession(StartPayload("MANUAL")).stringify()
                pluginAction(startNewSession) { status, content ->
                    status shouldBe HttpStatusCode.OK
                    val startSession = content!!.parseJsonData<StartSession>()
                    plugUi.activeSessions()!!.run { count shouldBe 1 }
                    runWithSession(startSession.payload.sessionId) {
                        val gt = build.entryPoint()
                        gt.test1()
                        gt.test2()
                    }

                    val startNewSession2 = StartNewSession(StartPayload("AUTO")).stringify()

                    pluginAction(startNewSession2) { status2, content2 ->
                        status2 shouldBe HttpStatusCode.OK

                        val startSession2 = content2!!.parseJsonData<StartSession>()
                        plugUi.activeSessions()!!.run { count shouldBe 2 }
                        runWithSession(startSession2.payload.sessionId) {
                            val gt = build.entryPoint()
                            gt.test3()
                        }

                        pluginAction(StopSession(SessionPayload(startSession.payload.sessionId)).stringify()).join()

                        plugUi.activeSessions()!!.apply {
                            count shouldBe 1
                            testTypes shouldBe setOf("AUTO")
                        }

                        pluginAction(StopSession(SessionPayload(startSession2.payload.sessionId)).stringify()).join()
                    }.join()
                }.join()
                plugUi.activeSessions()!!.count shouldBe 0
            }
        }
    }
}
