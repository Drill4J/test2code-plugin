package com.epam.drill.plugins.coverage.e2e


import com.epam.drill.builds.*
import com.epam.drill.e2e.*
import com.epam.drill.plugins.coverage.*
import io.kotlintest.*
import org.junit.jupiter.api.*


class CoverageByPackagesTest : E2EPluginTest() {

    @Test
    fun `E2E coverage by packages test`() {
        createSimpleAppWithPlugin<CoverageSocketStreams> {
            connectAgent<Build1> { plugUi, _ ->

                plugUi.coverageByPackages()?.first()?.apply {
                    id shouldBe "vsu9sbxes5bl"
                    coveredClassesCount shouldBe 0
                    name shouldBe "com/epam/test"
                    coverage shouldBe 0.0
                    totalClassesCount shouldBe 1
                    coveredClassesCount shouldBe 0
                    totalClassesCount shouldBe 1
                    coveredClassesCount shouldBe 0
                    classes.size shouldNotBe 0
                }
            }
        }
    }
}