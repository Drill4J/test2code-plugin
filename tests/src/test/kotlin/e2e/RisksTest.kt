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
import kotlin.test.*


class RisksTest : E2EPluginTest() {

    @Test
    fun `cover all risks during 2 builds`() {
        createSimpleAppWithPlugin<CoverageSocketStreams> {
            connectAgent<Build1> { plugUi, build ->

                plugUi.activeScope()!!.coverage.percentage shouldBe 0.0

                val startNewSession = StartNewSession(StartPayload("MANUAL")).stringify()
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
                plugUi.activeScope()!!.coverage shouldNotBe 0.0

                val switchScope = SwitchActiveScope(
                    ActiveScopeChangePayload(
                        scopeName = "new2",
                        savePrevScope = true,
                        prevScopeEnabled = true
                    )
                ).stringify()

                pluginAction(switchScope)

                delay(300)
            }.reconnect<Build2> { plugUi, build ->
                plugUi.risks()!!.apply {
                    size shouldBe 4
                    first { it.type == RiskType.NEW }.apply {
                        name shouldBe "firstMethod"
                        desc shouldBe "(): void"
                    }
                    first { it.type == RiskType.MODIFIED }.apply {
                        name shouldBe "test1"
                        desc shouldBe "(): void"
                    }
                }

                val startNewSession = StartNewSession(StartPayload("MANUAL")).stringify()
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
                delay(300)//todo move it to core library
                plugUi.activeSessions()!!.count shouldBe 0

                val switchScope = SwitchActiveScope(
                    ActiveScopeChangePayload(
                        scopeName = "new2",
                        savePrevScope = true,
                        prevScopeEnabled = true
                    )
                ).stringify()
                pluginAction(switchScope)
            }
        }
    }
}
