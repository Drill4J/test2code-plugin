package com.epam.drill.plugins.test2code.e2e

import com.epam.drill.builds.*
import com.epam.drill.e2e.*
import com.epam.drill.plugins.test2code.*
import io.kotlintest.*
import kotlinx.coroutines.*
import org.junit.jupiter.api.*

class BuildMethodsTests : E2EPluginTest() {

    @Test
    fun `deploy 2 builds without coverage`() {
        createSimpleAppWithPlugin<CoverageSocketStreams> {
            connectAgent<Build1> { plugUi, _ ->
                plugUi.methods()!!.apply {
                    all.total shouldBe 4
                    new.total shouldBe 0
                    modified.total shouldBe 0
                    deleted.covered shouldBe 0
                    deleted.total shouldBe 0
                }
                delay(300)
            }.reconnect<Build2> { plugUi, _ ->
                plugUi.methods()!!.apply {
                    all.total shouldBe 5
                    new.total shouldBe 1
                    modified.total shouldBe 3
                    deleted.covered shouldBe 0
                    deleted.total shouldBe 0
                }
            }
        }
    }
}
