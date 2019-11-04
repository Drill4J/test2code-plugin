package com.epam.drill.plugins.coverage.e2e


import com.epam.drill.e2e.AbstarctE2EPluginTest
import com.epam.drill.endpoints.plugin.SubscribeInfo
import com.epam.drill.plugins.coverage.CoverageSocketStreams
import com.epam.drill.plugins.coverage.InitInfo
import com.epam.drill.plugins.coverage.Initialized
import com.epam.drill.plugins.coverage.sendEvent
import io.kotlintest.shouldBe
import io.kotlintest.shouldNotBe
import org.junit.Test


class CoverageByPackagesTest : AbstarctE2EPluginTest<CoverageSocketStreams>() {

    @Test(timeout = 10000)
    fun `E2E coverage by packages test`() {
        createSimpleAppWithPlugin<CoverageSocketStreams> {
            connectAgent(setOf("DrillExtension1.class")) { plugUi, agent ->
                plugUi.subscribe(SubscribeInfo(agentId, buildVersionHash))
                agent.sendEvent(InitInfo(classesCount, "asdad"))
                agent.sendEvent(Initialized())
                plugUi.coverageByPackages()?.first()?.apply {
                    id shouldBe "187n83tdei8po"
                    coveredClassesCount shouldBe 0
                    name shouldBe "org/springframework/samples/petclinic/system"
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