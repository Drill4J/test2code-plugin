package com.epam.drill.plugins.test2code.e2e

import com.epam.drill.builds.*
import com.epam.drill.e2e.*
import com.epam.drill.plugins.test2code.*
import io.kotlintest.*
import org.junit.jupiter.api.*

class BuildMethodsTests : E2EPluginTest() {

    @Test
    fun `deploy 2 builds without coverage`() {
        createSimpleAppWithPlugin<CoverageSocketStreams> {
            connectAgent<Build1> { plugUi, _ ->
                plugUi.methods()!!.apply {
                    totalMethods.totalCount shouldBe 4
                    newMethods.totalCount shouldBe 4
                    modifiedNameMethods.totalCount shouldBe 0
                    modifiedDescMethods.totalCount shouldBe 0
                    modifiedBodyMethods.totalCount shouldBe 0
                    deletedMethods.totalCount shouldBe 0
                    deletedCoveredMethodsCount shouldBe 0
                    allModifiedMethods.methods.size shouldBe 0
                }
            }.reconnect<Build2> { plugUi, _ ->
                plugUi.methods()//FIXME EPMDJ-2533 extra write
                plugUi.methods()!!.apply {
                    totalMethods.totalCount shouldBe 5
                    newMethods.totalCount shouldBe 1
                    modifiedNameMethods.totalCount shouldBe 0
                    modifiedDescMethods.totalCount shouldBe 0
                    modifiedBodyMethods.totalCount shouldBe 3
                    deletedMethods.totalCount shouldBe 0
                    deletedCoveredMethodsCount shouldBe 0
                    allModifiedMethods.methods.size shouldBe 3
                }
            }
        }
    }
}
