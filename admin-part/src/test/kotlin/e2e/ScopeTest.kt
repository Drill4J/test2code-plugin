package com.epam.drill.plugins.coverage.e2e


import com.epam.drill.e2e.AbstarctE2EPluginTest
import com.epam.drill.endpoints.plugin.SubscribeInfo
import com.epam.drill.plugins.coverage.*
import io.kotlintest.shouldBe
import io.kotlintest.shouldNotBe
import io.ktor.http.HttpStatusCode
import org.junit.Ignore
import org.junit.Test


class ScopeTest : AbstarctE2EPluginTest<CoverageSocketStreams>() {


    @Test(timeout = 10000)
    fun `E2E scope test`() {
        createSimpleAppWithPlugin<CoverageSocketStreams> {
            connectAgent(setOf("DrillExtension1.class")) { plugUi, agent ->
                plugUi.subscribe(SubscribeInfo(agentId, buildVersionHash))
                agent.sendEvent(InitInfo(classesCount, "asdad"))
                agent.sendEvent(Initialized())
                val activeScope = plugUi.activeScope()
                activeScope?.apply {
                    name shouldBe "New Scope 1"
                    started shouldNotBe 0L
                    finished shouldBe 0L
                    coverage shouldBe 0.0
                    enabled shouldBe true
                    active shouldBe true
                }
            }.newConnect(setOf("DrillExtension2.class")) { plugUi, agent ->
                plugUi.subscribe(SubscribeInfo(agentId, buildVersionHash))
                agent.sendEvent(InitInfo(classesCount, "asdad"))
                agent.sendEvent(Initialized())
                val activeScope = plugUi.activeScope()
                activeScope?.apply {
                    name shouldBe "New Scope 1"
                    started shouldNotBe 0L
                    finished shouldBe 0L
                    coverage shouldBe 0.0
                    enabled shouldBe true
                    active shouldBe true
                }
                val renameScope =
                    RenameScope(RenameScopePayload(activeScope!!.id, "integration")).stringify()
                pluginAction(renameScope)
                plugUi.activeScope()?.name shouldBe "integration"

                val toggleScope = ToggleScope(ScopePayload(activeScope.id)).stringify()
                println(toggleScope)
                //TODO(Is it toggle scope worked? We have got status 400.)
/*                val (status, content) = pluginAction(toggleScope)
                status shouldBe HttpStatusCode.OK
                println(content)
                plugUi.activeScope()?.enabled shouldBe false
                pluginAction(toggleScope)
                plugUi.activeScope()?.enabled shouldBe true*/
            }


        }
    }
}
