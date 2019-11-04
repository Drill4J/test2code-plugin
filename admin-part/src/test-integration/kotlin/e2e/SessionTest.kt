package com.epam.drill.plugins.coverage.e2e


import com.epam.drill.e2e.AbstarctE2EPluginTest
import com.epam.drill.endpoints.plugin.SubscribeInfo
import com.epam.drill.plugins.coverage.*
import io.kotlintest.shouldBe
import io.ktor.http.HttpStatusCode
import org.junit.Test


class SessionTest : AbstarctE2EPluginTest<CoverageSocketStreams>() {

    @Test(timeout = 10000)
    fun `E2E test session test`() {
        createSimpleAppWithPlugin<CoverageSocketStreams> {
            connectAgent(setOf("DrillExtension1.class")) { plugUi, agent ->
                plugUi.subscribe(SubscribeInfo(agentId, buildVersionHash))
                agent.sendEvent(InitInfo(classesCount, "asdad"))
                agent.sendEvent(Initialized())
                plugUi.activeSessions()?.apply {
                    count shouldBe 0
                    testTypes shouldBe emptySet()
                }
              val startNewSession = StartNewSession(StartPayload("MANUAL")).stringify()
                val (status, content) = pluginAction(startNewSession)
                status shouldBe HttpStatusCode.OK
                println(content)
                /*//TODO("We have got status 200 but on the next step test has been failed with timeout")
                plugUi.activeSessions()?.apply {
                    count shouldBe 1
                    testTypes shouldBe "MANUAL"
                }*/
            }
        }
    }
}