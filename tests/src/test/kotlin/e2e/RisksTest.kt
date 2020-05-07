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
import org.junit.jupiter.api.*


class RisksTest : E2EPluginTest() {

    @Test
    fun `cover all risks during 2 builds`() {
        createSimpleAppWithPlugin<CoverageSocketStreams> {
            connectAgent<Build1> { plugUi, build ->

                plugUi.activeScope()!!.coverage.ratio shouldBe 0.0

                plugUi.risks()!!.apply {
                    newMethods.size shouldBe 4
                    newMethods.first().name shouldBe "Test"
                    newMethods.first().desc shouldBe "(): void"
                    modifiedMethods shouldBe emptyList()
                }
                val startNewSession = StartNewSession(StartPayload("MANUAL")).stringify()
                pluginAction(startNewSession) { status, content ->
                    status shouldBe HttpStatusCode.OK
                    val startSession = content!!.parseJsonData<StartSession>()

                    runWithSession(startSession.payload.sessionId) {
                        val gt = build.entryPoint()
                        gt.test1()
                        gt.test2()
                        gt.test3()
                    }
                    pluginAction(StopSession(SessionPayload(startSession.payload.sessionId)).stringify())
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

                plugUi.risks()!!.apply {
                    newMethods.size shouldBe 0
                    modifiedMethods shouldBe emptyList()
                }
            }.reconnect<Build2> { plugUi, build ->
                plugUi.risks()!!.apply {
                    newMethods.count() shouldBe 1
                    modifiedMethods.size shouldBe 3
                    newMethods.first().name shouldBe "firstMethod"
                    newMethods.first().desc shouldBe "(): void"
                    modifiedMethods.first().name shouldBe "test1"
                    modifiedMethods.first().desc shouldBe "(): void"
                }

                val startNewSession = StartNewSession(StartPayload("MANUAL")).stringify()
                pluginAction(startNewSession) { status, content ->
                    status shouldBe HttpStatusCode.OK
                    val startSession = content!!.parseJsonData<StartSession>()

                    runWithSession(startSession.payload.sessionId) {
                        val gt = build.entryPoint()
                        gt.test1()
                        gt.test2()
                        gt.test3()
                    }

                    val stopSession = StopSession(SessionPayload(startSession.payload.sessionId)).stringify()
                    pluginAction(stopSession) { status1, _ -> status1 shouldBe HttpStatusCode.OK }
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

                plugUi.risks()!!.apply {
                    newMethods.count() shouldBe 0
                    modifiedMethods.count() shouldBe 0
                }
            }
        }
    }
}
