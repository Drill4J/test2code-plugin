package com.epam.drill.plugins.coverage.e2e


import com.epam.drill.e2e.AbstarctE2EPluginTest
import com.epam.drill.endpoints.plugin.SubscribeInfo
import com.epam.drill.plugins.coverage.CoverageSocketStreams
import com.epam.drill.plugins.coverage.InitInfo
import com.epam.drill.plugins.coverage.Initialized
import com.epam.drill.plugins.coverage.sendEvent
import io.kotlintest.shouldBe
import org.junit.Test


class RisksTest : AbstarctE2EPluginTest<CoverageSocketStreams>() {

    @Test(timeout = 10000)
    fun `E2E risks test`() {
        createSimpleAppWithPlugin<CoverageSocketStreams> {
            connectAgent(setOf("DrillExtension1.class")) { plugUi, agent ->
                plugUi.subscribe(SubscribeInfo(agentId, buildVersionHash))
                agent.sendEvent(InitInfo(classesCount, "asdad"))
                agent.sendEvent(Initialized())
                plugUi.risks()?.apply {
                    newMethods.first().name shouldBe "DrillExtension"
                    newMethods.first().desc shouldBe "(): void"
                    modifiedMethods shouldBe emptyList()
                }
            }.newConnect(setOf("DrillExtension2.class")) { plugUi, agent ->
                plugUi.subscribe(SubscribeInfo(agentId, buildVersionHash))
                agent.sendEvent(InitInfo(classesCount, "asdad"))
                agent.sendEvent(Initialized())
                plugUi.risks()?.apply {
                    newMethods.first().name shouldBe "DrillExtension"
                    newMethods.first().desc shouldBe "(String): void"
                    modifiedMethods shouldBe emptyList()
                }
            }
        }
    }
}
