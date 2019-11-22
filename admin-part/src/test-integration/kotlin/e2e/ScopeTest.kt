package com.epam.drill.plugins.coverage.e2e


import com.epam.drill.builds.*
import com.epam.drill.e2e.*
import com.epam.drill.e2e.plugin.*
import com.epam.drill.plugins.coverage.*
import io.kotlintest.*
import io.ktor.http.*
import org.junit.jupiter.api.*


class ScopeTest : E2EPluginTest() {


    @Test
    fun `E2E scope test`() {
        createSimpleAppWithPlugin<CoverageSocketStreams> {
            connectAgent<Build1> { plugUi, _ ->

                val activeScope = plugUi.activeScope()
                activeScope!!.apply {
                    name shouldBe "New Scope 1"
                    started shouldNotBe 0L
                    finished shouldBe 0L
                    coverage shouldBe 0.0
                    enabled shouldBe true
                    active shouldBe true
                }
            }.reconnect<Build2> { plugUi, _ ->

                val activeScope = plugUi.activeScope()
                activeScope!!.apply {
                    name shouldBe "New Scope 1"
                    started shouldNotBe 0L
                    finished shouldBe 0L
                    coverage shouldBe 0.0
                    enabled shouldBe true
                    active shouldBe true
                }
                val renameScope =
                    RenameScope(RenameScopePayload(activeScope.id, "integration")).stringify()
                pluginAction(renameScope)
                plugUi.activeScope()!!.name shouldBe "integration"

                val switchActiveScopeWrong = SwitchActiveScope(ActiveScopeChangePayload("integration")).stringify()
                val (status1, _) = pluginAction(switchActiveScopeWrong)
                status1 shouldBe HttpStatusCode.BadRequest

                val switchActiveScope = SwitchActiveScope(ActiveScopeChangePayload("scope integration")).stringify()
                val (status2, _) = pluginAction(switchActiveScope)
                status2 shouldBe HttpStatusCode.OK
                plugUi.activeScope()!!.name shouldBe "scope integration"
            }

        }
    }

    @Test
    fun `Finish active scope and drop it after that`() {
        lateinit var droppedScopeId: String
        createSimpleAppWithPlugin<CoverageSocketStreams> {
            connectAgent<Build1> { plugUi, build ->
                plugUi.buildCoverage()!!.coverage shouldBe 0.0
                plugUi.activeSessions()!!.run {
                    count shouldBe 0
                    testTypes shouldBe emptySet()
                }
                plugUi.activeScope()!!.apply {
                    coverage shouldBe 0.0
                    droppedScopeId = id
                }
                val startNewSession = StartNewSession(StartPayload("MANUAL")).stringify()
                val (status, content) = pluginAction(startNewSession)
                status shouldBe HttpStatusCode.OK
                val startSession = commonSerDe.parse(commonSerDe.actionSerializer, content!!) as StartSession
                plugUi.activeSessions()!!.run { count shouldBe 1 }
                runWithSession(startSession.payload.sessionId) {
                    val gt = build.entryPoint()
                    gt.test1()
                    gt.test2()
                    gt.test3()
                }
                pluginAction(StopSession(SessionPayload(startSession.payload.sessionId)).stringify())
                plugUi.activeSessions()!!.count shouldBe 0
                plugUi.activeScope()!!.coverage shouldBe 100.0
                val switchScope = SwitchActiveScope(
                    ActiveScopeChangePayload(
                        scopeName = "new2",
                        savePrevScope = true,
                        prevScopeEnabled = true
                    )
                ).stringify()
                pluginAction(switchScope)
                plugUi.buildCoverage()!!.coverage shouldBe 100.0
                pluginAction(DropScope(ScopePayload(droppedScopeId)).stringify())
                plugUi.buildCoverage()!!.coverage shouldBe 0.0
                plugUi.activeScope()!!.id shouldNotBe droppedScopeId
                plugUi.scopes() shouldBe null
            }
        }
    }

    @Test
    fun `Finish active scope with ignoring and reignore after that`() {
        lateinit var ignoredScopeId: String
        createSimpleAppWithPlugin<CoverageSocketStreams> {
            connectAgent<Build1> { plugUi, build ->
                plugUi.activeSessions()!!.run {
                    count shouldBe 0
                    testTypes shouldBe emptySet()
                }
                plugUi.activeScope()!!.apply {
                    coverage shouldBe 0.0
                    ignoredScopeId = id
                }
                val startNewSession = StartNewSession(StartPayload("MANUAL")).stringify()
                val (status, content) = pluginAction(startNewSession)
                status shouldBe HttpStatusCode.OK
                val startSession = commonSerDe.parse(commonSerDe.actionSerializer, content!!) as StartSession
                plugUi.activeSessions()!!.run { count shouldBe 1 }
                runWithSession(startSession.payload.sessionId) {
                    val gt = build.entryPoint()
                    gt.test1()
                    gt.test2()
                    gt.test3()
                }
                pluginAction(StopSession(SessionPayload(startSession.payload.sessionId)).stringify())
                plugUi.activeSessions()!!.count shouldBe 0
                plugUi.activeScope()!!.coverage shouldBe 100.0
                val switchScope = SwitchActiveScope(
                    ActiveScopeChangePayload(
                        scopeName = "new2",
                        savePrevScope = true,
                        prevScopeEnabled = true
                    )
                ).stringify()
                pluginAction(switchScope)
                plugUi.buildCoverage()!!.coverage shouldBe 0.0
                pluginAction(ToggleScope(ScopePayload(ignoredScopeId)).stringify())
                plugUi.buildCoverage()!!.coverage shouldBe 100.0
            }
        }
    }
}

