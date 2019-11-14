package com.epam.drill.plugins.coverage.e2e


import com.epam.drill.builds.*
import com.epam.drill.e2e.*
import com.epam.drill.plugins.coverage.*
import io.kotlintest.*
import io.ktor.http.*
import org.junit.jupiter.api.*

class SessionTest : E2EPluginTest<CoverageSocketStreams>() {


    @RepeatedTest(2)
    fun `E2E test session test`() {
        createSimpleAppWithPlugin<CoverageSocketStreams> {
            connectAgent<Build1> { plugUi, build ->

                plugUi.activeSessions()?.run {
                    count shouldBe 0
                    testTypes shouldBe emptySet()
                }

                val startNewSession = StartNewSession(StartPayload("MANUAL")).stringify()
                val (status, content) = pluginAction(startNewSession)
                status shouldBe HttpStatusCode.OK
                val startSession = commonSerDe.parse(commonSerDe.actionSerializer, content!!) as StartSession

                plugUi.activeSessions()?.run { count shouldBe 1 }

                runWithSession(startSession.payload.sessionId) {
                    val gt = build.entryPoint()
                    gt.test1()
                    gt.test2()
                    gt.test3()
                }

                pluginAction(StopSession(SessionPayload(startSession.payload.sessionId)).stringify())
                plugUi.activeSessions()?.count shouldBe 0

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

                plugUi.activeSessions()?.run {
                    count shouldBe 0
                    testTypes shouldBe emptySet()
                }

                plugUi.activeScope()?.coverage shouldBe 0.0
                plugUi.buildCoverage()?.coverage shouldBe 0.0

                val startNewSession = StartNewSession(StartPayload("MANUAL")).stringify()
                val (status, content) = pluginAction(startNewSession)
                status shouldBe HttpStatusCode.OK
                val startSession = commonSerDe.parse(commonSerDe.actionSerializer, content!!) as StartSession

                plugUi.activeSessions()?.run { count shouldBe 1 }

                runWithSession(startSession.payload.sessionId) {
                    val gt = build.entryPoint()
                    gt.test1()
                    gt.test2()
                }

                pluginAction(StopSession(SessionPayload(startSession.payload.sessionId)).stringify())
                plugUi.activeSessions()?.count shouldBe 0
                plugUi.activeScope()?.coverage shouldBe 53.333333333333336

                val startNewSession2 = StartNewSession(StartPayload("AUTO")).stringify()
                val (status2, content2) = pluginAction(startNewSession2)
                status2 shouldBe HttpStatusCode.OK
                val startSession2 = commonSerDe.parse(commonSerDe.actionSerializer, content2!!) as StartSession

                plugUi.activeSessions()?.run { count shouldBe 1 }

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
                pluginAction(switchScope)
                plugUi.activeSessions()?.count shouldBe 0
                plugUi.activeScope()?.coverage shouldBe 0.0
                plugUi.buildCoverage()?.coverage shouldBe 53.333333333333336
            }
        }
    }

    @Test
    fun `Start and finish 2 session`() {
        createSimpleAppWithPlugin<CoverageSocketStreams> {
            connectAgent<Build1> { plugUi, build ->
                plugUi.activeSessions()?.run {
                    count shouldBe 0
                    testTypes shouldBe emptySet()
                }

                plugUi.activeScope()?.coverage shouldBe 0.0
                plugUi.buildCoverage()?.coverage shouldBe 0.0
                val startNewSession = StartNewSession(StartPayload("MANUAL")).stringify()
                val (status, content) = pluginAction(startNewSession)
                status shouldBe HttpStatusCode.OK
                val startSession = commonSerDe.parse(commonSerDe.actionSerializer, content!!) as StartSession
                plugUi.activeSessions()?.run { count shouldBe 1 }
                runWithSession(startSession.payload.sessionId) {
                    val gt = build.entryPoint()
                    gt.test1()
                    gt.test2()
                }

                val startNewSession2 = StartNewSession(StartPayload("AUTO")).stringify()
                val (status2, content2) = pluginAction(startNewSession2)
                status2 shouldBe HttpStatusCode.OK

                val startSession2 = commonSerDe.parse(commonSerDe.actionSerializer, content2!!) as StartSession
                plugUi.activeSessions()?.run { count shouldBe 2 }
                runWithSession(startSession2.payload.sessionId) {
                    val gt = build.entryPoint()
                    gt.test3()
                }

                pluginAction(StopSession(SessionPayload(startSession.payload.sessionId)).stringify())
                plugUi.activeSessions()?.apply {
                    count shouldBe 1
                    testTypes shouldBe setOf("AUTO")
                }

                pluginAction(StopSession(SessionPayload(startSession2.payload.sessionId)).stringify())
                plugUi.activeSessions()?.count shouldBe 0
            }
        }
    }
}