package com.epam.drill.plugins.coverage.e2e


import com.epam.drill.builds.*
import com.epam.drill.e2e.*
import com.epam.drill.plugins.coverage.*
import io.kotlintest.*


class RisksTest : E2EPluginTest<CoverageSocketStreams>() {

    @org.junit.jupiter.api.Test
    fun `E2E risks test`() {
        createSimpleAppWithPlugin<CoverageSocketStreams> {
            connectAgent<Build1> { plugUi, _ ->

                plugUi.risks()?.apply {
                    newMethods.first().name shouldBe "Test"
                    newMethods.first().desc shouldBe "(): void"
                    modifiedMethods shouldBe emptyList()
                }

            }.reconnect<Build2> { plugUi, _ ->

                plugUi.risks()?.apply {
                    newMethods.first().name shouldBe "firstMethod"
                    newMethods.first().desc shouldBe "(): void"
                    modifiedMethods.count() shouldBe 3
                }
            }
        }
    }
}
