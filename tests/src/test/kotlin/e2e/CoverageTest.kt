package com.epam.drill.plugins.test2code.e2e

import com.epam.drill.builds.*
import com.epam.drill.e2e.*
import com.epam.drill.plugins.test2code.*
import io.kotlintest.*
import kotlinx.coroutines.*
import kotlin.test.*

class CoverageTest : E2EPluginTest() {

    @Test
    fun `e2e coverage test`() {
        createSimpleAppWithPlugin<CoverageSocketStreams> {
            connectAgent<Build1> { plugUi, _ ->
                plugUi.buildCoverage()!!.apply {
                    percentage shouldBe 0.0
                    byTestType shouldBe emptyList()
                }
                delay(100)
            }.reconnect<Build2> { plugUi, _ ->
                plugUi.buildCoverage()!!.apply {
                    percentage shouldBe 0.0
                    byTestType shouldBe emptyList()
                }
            }
        }
    }
}
