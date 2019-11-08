package com.epam.drill.plugins.coverage.e2e


import com.epam.drill.builds.*
import com.epam.drill.e2e.*
import com.epam.drill.plugins.coverage.*
import io.kotlintest.*
import io.ktor.http.*
import org.junit.jupiter.api.*

class SessionTest : AbstarctE2EPluginTest<CoverageSocketStreams>() {


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

                plugUi.activeSessions()?.run {
                    count shouldBe 1
                    testTypes.first() shouldBe "MANUAL"
                }

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
}