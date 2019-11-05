package com.epam.drill.plugins.coverage.e2e


import com.epam.drill.builds.*
import com.epam.drill.e2e.*
import com.epam.drill.plugins.coverage.*
import io.kotlintest.*
import io.ktor.http.*


class ScopeTest : E2EPluginTest<CoverageSocketStreams>() {


    @org.junit.jupiter.api.Test
    fun `E2E scope test`() {
        createSimpleAppWithPlugin<CoverageSocketStreams> {
            connectAgent<Build1> { plugUi, _ ->

                val activeScope = plugUi.activeScope()
                activeScope?.apply {
                    name shouldBe "New Scope 1"
                    started shouldNotBe 0L
                    finished shouldBe 0L
                    coverage shouldBe 0.0
                    enabled shouldBe true
                    active shouldBe true
                }
            }.reconnect<Build2> { plugUi, _ ->

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

                val switchActiveScopeWrong = SwitchActiveScope(ActiveScopeChangePayload("integration")).stringify()
                val (status1, _) = pluginAction(switchActiveScopeWrong)
                status1 shouldBe HttpStatusCode.BadRequest

                val switchActiveScope = SwitchActiveScope(ActiveScopeChangePayload("scope integration")).stringify()
                val (status2, _) = pluginAction(switchActiveScope)
                status2 shouldBe HttpStatusCode.OK
                plugUi.activeScope()?.name shouldBe "scope integration"
            }

        }
    }
}
