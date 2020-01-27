package com.epam.drill.plugins.test2code.e2e


import com.epam.drill.builds.*
import com.epam.drill.e2e.*
import com.epam.drill.e2e.plugin.*
import com.epam.drill.plugins.test2code.*
import io.kotlintest.*
import io.ktor.http.*
import org.junit.jupiter.api.*


class ScopeTest : E2EPluginTest() {


    @Test
    fun `e2e scope test`() {
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
                pluginAction(renameScope).join()
                plugUi.activeScope()//FIXME EPMDJ-2270 extra write
                plugUi.activeScope()!!.name shouldBe "integration"

                val switchActiveScopeWrong = SwitchActiveScope(ActiveScopeChangePayload("integration")).stringify()
                pluginAction(switchActiveScopeWrong) { status, _ ->
                    status shouldBe HttpStatusCode.BadRequest
                }.join()
                val switchActiveScope = SwitchActiveScope(ActiveScopeChangePayload("scope integration")).stringify()
                pluginAction(switchActiveScope) { status, _ ->
                    status shouldBe HttpStatusCode.OK
                }.join()
                plugUi.activeScope()!!.name shouldBe "scope integration"
            }

        }
    }

    @Test
    fun `finish active scope and drop it after that`() {
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
                pluginAction(startNewSession) { status, content ->
                    status shouldBe HttpStatusCode.OK
                    val startSession = commonSerDe.parse(commonSerDe.actionSerializer, content!!) as StartSession
                    plugUi.activeSessions()!!.run { count shouldBe 1 }
                    runWithSession(startSession.payload.sessionId) {
                        val gt = build.entryPoint()
                        gt.test1()
                        gt.test2()
                        gt.test3()
                    }
                    pluginAction(StopSession(SessionPayload(startSession.payload.sessionId)).stringify()).join()
                }.join()
                plugUi.activeSessions()!!.count shouldBe 0
                plugUi.activeScope()!!.coverage shouldBe 100.0
                val switchScope = SwitchActiveScope(
                    ActiveScopeChangePayload(
                        scopeName = "new2",
                        savePrevScope = true,
                        prevScopeEnabled = true
                    )
                ).stringify()
                pluginAction(switchScope).join()
                plugUi.buildCoverage()!!.coverage shouldBe 100.0
                pluginAction(DropScope(ScopePayload(droppedScopeId)).stringify()).join()
                plugUi.buildCoverage()!!.coverage shouldBe 0.0
                plugUi.activeScope()!!.id shouldNotBe droppedScopeId
                plugUi.scopes() shouldBe null
            }
        }
    }

    @Test
    fun `finish active scope with ignoring and reignore after that`() {
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
                pluginAction(startNewSession) { status, content ->
                    status shouldBe HttpStatusCode.OK
                    val startSession = commonSerDe.parse(commonSerDe.actionSerializer, content!!) as StartSession
                    plugUi.activeSessions()!!.run { count shouldBe 1 }
                    runWithSession(startSession.payload.sessionId) {
                        val gt = build.entryPoint()
                        gt.test1()
                        gt.test2()
                        gt.test3()
                    }
                    pluginAction(StopSession(SessionPayload(startSession.payload.sessionId)).stringify()).join()
                }.join()
                plugUi.activeSessions()!!.count shouldBe 0
                plugUi.activeScope()!!.coverage shouldBe 100.0
                val switchScope = SwitchActiveScope(
                    ActiveScopeChangePayload(
                        scopeName = "new2",
                        savePrevScope = true,
                        prevScopeEnabled = true
                    )
                ).stringify()
                pluginAction(switchScope).join()
                plugUi.buildCoverage()!!.coverage shouldBe 0.0
                pluginAction(ToggleScope(ScopePayload(ignoredScopeId)).stringify()).join()
                plugUi.buildCoverage()!!.coverage shouldBe 100.0
            }
        }
    }
}

