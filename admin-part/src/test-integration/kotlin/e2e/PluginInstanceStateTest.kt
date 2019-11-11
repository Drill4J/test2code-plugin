package com.epam.drill.plugins.coverage.e2e

import com.epam.drill.builds.*
import com.epam.drill.e2e.*
import com.epam.drill.plugins.coverage.*
import io.kotlintest.*
import io.kotlintest.matchers.boolean.*
import io.kotlintest.matchers.doubles.*
import io.ktor.http.*

class PluginInstanceStateTest : E2EPluginTest<CoverageSocketStreams>() {

    fun `E2E test session test`() {
        createSimpleAppWithPlugin<CoverageSocketStreams>(true, true) {
            connectAgent<Build1> { plugUi, build ->
                plugUi.coverageByPackages()
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
                val switchScope = SwitchActiveScope(
                    ActiveScopeChangePayload(
                        scopeName = "new2",
                        savePrevScope = true,
                        prevScopeEnabled = true
                    )
                ).stringify()
                pluginAction(switchScope)

                plugUi.coverageByPackages()?.apply {
                    first().coverage shouldBeGreaterThan 0.0
                }

            }.reconnect<Build2> { plugUi, _ ->
                plugUi.testsToRun()?.apply {
                    testsToRun.isNotEmpty().shouldBeTrue()
                }
            }
        }
    }
}
