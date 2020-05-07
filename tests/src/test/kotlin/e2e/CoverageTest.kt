package com.epam.drill.plugins.test2code.e2e


import com.epam.drill.builds.*
import com.epam.drill.e2e.*
import com.epam.drill.plugins.test2code.*
import io.kotlintest.*
import org.junit.jupiter.api.*


class CoverageTest : E2EPluginTest() {

    @Test
    fun `e2e coverage test`() {
        createSimpleAppWithPlugin<CoverageSocketStreams> {
            connectAgent<Build1> { plugUi, _ ->
                plugUi.buildCoverage()!!.apply {
                    ratio shouldBe 0.0
                    diff shouldBe 0.0
                    prevBuildVersion shouldBe ""
                    byTestType shouldBe emptyMap()
                    arrow shouldBe null
                }
            }.reconnect<Build2> { plugUi, _ ->
                plugUi.buildCoverage()!!.apply {
                    ratio shouldBe 0.0
                    diff shouldBe 0.0
                    prevBuildVersion shouldBe "30507"
                    byTestType shouldBe emptyMap()
                    arrow shouldBe null
                }
            }
        }
    }
}
