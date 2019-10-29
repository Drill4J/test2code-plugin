package com.epam.drill.plugins.coverage.e2e


import com.epam.drill.e2e.AbstarctE2EPluginTest
import com.epam.drill.endpoints.plugin.SubscribeInfo
import com.epam.drill.plugins.coverage.CoverageSocketStreams
import com.epam.drill.plugins.coverage.InitInfo
import com.epam.drill.plugins.coverage.Initialized
import com.epam.drill.plugins.coverage.sendEvent
import io.kotlintest.shouldBe
import org.junit.Test


class CoverageTest : AbstarctE2EPluginTest<CoverageSocketStreams>() {

    @Test(timeout = 10000)
    fun `E2E coverage test`() {
        createSimpleAppWithPlugin<CoverageSocketStreams> {
            connectAgent(setOf("DrillExtension1.class")) { plugUi, agent ->
                plugUi.subscribe(SubscribeInfo(agentId, buildVersionHash))
                agent.sendEvent(InitInfo(classesCount, "asdad"))
                agent.sendEvent(Initialized())
                plugUi.coverage()?.apply {
                    coverage shouldBe 0.0
                    diff shouldBe 0.0
                    previousBuildInfo.first shouldBe ""
                    previousBuildInfo.second shouldBe ""
                    coverageByType shouldBe emptyMap()
                    arrow shouldBe null
                }
            }.newConnect(setOf("DrillExtension2.class")) { plugUi, agent ->

                plugUi.subscribe(SubscribeInfo(agentId, buildVersionHash))

                agent.sendEvent(InitInfo(classesCount, "asdad"))
                agent.sendEvent(Initialized())
                plugUi.coverage()?.apply {
                    coverage shouldBe 0.0
                    diff shouldBe 0.0
                    previousBuildInfo.first shouldBe "38187"
                    previousBuildInfo.second shouldBe "sad"
                    coverageByType shouldBe emptyMap()
                    arrow shouldBe null
                }
            }
        }
    }
}
