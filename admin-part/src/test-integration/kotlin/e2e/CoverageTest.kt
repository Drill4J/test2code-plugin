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


class CoverageTest : AbstarctE2EPluginTest<CoverageSocketStreams>() {

    @org.junit.jupiter.api.Test
    fun `E2E coverage test`() {
        createSimpleAppWithPlugin<CoverageSocketStreams> {
            connectAgent<Build1> { plugUi, _ ->

                plugUi.buildCoverage()?.apply {
                    coverage shouldBe 0.0
                    diff shouldBe 0.0
                    previousBuildInfo.first shouldBe ""
                    previousBuildInfo.second shouldBe ""
                    coverageByType shouldBe emptyMap()
                    arrow shouldBe null
                }
            }.reconnect<Build2> { plugUi, _ ->



                plugUi.buildCoverage()?.apply {
                    coverage shouldBe 0.0
                    diff shouldBe 0.0
                    previousBuildInfo.first shouldBe "30507"
                    previousBuildInfo.second shouldBe "sad"
                    coverageByType shouldBe emptyMap()
                    arrow shouldBe null
                }
            }
        }
    }
}
