package com.epam.drill.plugins.coverage.e2e


import com.epam.drill.builds.*
import com.epam.drill.e2e.AbstarctE2EPluginTest
import com.epam.drill.endpoints.plugin.SubscribeInfo
import com.epam.drill.plugins.coverage.CoverageSocketStreams
import com.epam.drill.plugins.coverage.InitInfo
import com.epam.drill.plugins.coverage.Initialized
import com.epam.drill.plugins.coverage.sendEvent
import io.kotlintest.shouldBe
import org.junit.Test


class RisksTest : AbstarctE2EPluginTest<CoverageSocketStreams>() {

    @org.junit.jupiter.api.Test
    fun `E2E risks test`() {
        createSimpleAppWithPlugin<CoverageSocketStreams> {
            connectAgent<Build1> { plugUi, agent ->

                plugUi.risks()?.apply {
                    newMethods.first().name shouldBe "Test"
                    newMethods.first().desc shouldBe "(): void"
                    modifiedMethods shouldBe emptyList()
                }
            }.reconnect<Build2> { plugUi, agent ->

                plugUi.risks()?.apply {
                    newMethods.first().name shouldBe "firstMethod"
                    newMethods.first().desc shouldBe "(): void"
//                    modifiedMethods shouldBe emptyList()
                }
            }
        }
    }
}
