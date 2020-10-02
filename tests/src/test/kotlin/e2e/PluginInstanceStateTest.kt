package com.epam.drill.plugins.test2code.e2e

import com.epam.drill.builds.*
import com.epam.drill.e2e.*
import com.epam.drill.e2e.plugin.*
import com.epam.drill.plugins.test2code.*
import com.epam.drill.plugins.test2code.api.*
import com.epam.drill.plugins.test2code.common.api.*
import io.kotlintest.*
import io.kotlintest.matchers.doubles.*
import io.kotlintest.matchers.numerics.*
import io.ktor.http.*
import kotlinx.coroutines.*
import kotlin.test.*

class PluginInstanceStateTest : E2EPluginTest() {

    @Test
    fun `deploy build2 with finishing active scope and session on previous build`() {
        createSimpleAppWithPlugin<CoverageSocketStreams> {
            connectAgent<Build1> { plugUi, build ->
                plugUi.buildCoverage()
                plugUi.coveragePackages()
                plugUi.activeSessions()!!.run {
                    count shouldBe 0
                    testTypes shouldBe emptySet()
                }

                val startNewSession = StartNewSession(StartPayload("MANUAL")).stringify()
                pluginAction(startNewSession) { status, content ->
                    status shouldBe HttpStatusCode.OK
                    val startSession = content!!.parseJsonData<StartAgentSession>()

                    plugUi.activeSessions()!!.run { count shouldBe 1 }

                    runWithSession(startSession.payload.sessionId) {
                        val gt = build.entryPoint()
                        gt.test1()
                        gt.test2()
                        gt.test3()
                    }

                    pluginAction(StopAgentSession(AgentSessionPayload(startSession.payload.sessionId)).stringify()).join()
                }.join()

                plugUi.activeSessions()!!.count shouldBe 0
                val switchScope = SwitchActiveScope(
                    ActiveScopeChangePayload(
                        scopeName = "new2",
                        savePrevScope = true,
                        prevScopeEnabled = true
                    )
                ).stringify()
                pluginAction(switchScope) { status, _ ->
                    status shouldBe HttpStatusCode.OK
                    plugUi.buildCoverage()!!.apply {
                        count.covered shouldBeGreaterThan 0
                    }
                    plugUi.coveragePackages()!!.apply {
                        first().coverage shouldBeGreaterThan 0.0
                    }
                }.join()
                delay(100)
            }.reconnect<Build2> { plugUi, _ ->
                plugUi.buildCoverage()!!.apply {
                    count.covered shouldBe 0
                }
                plugUi.testsToRun()!!.apply {
                    testTypeToNames shouldNotBe emptyMap<Any, Any>()
                }
            }

        }
    }

    @Test
    fun `deploy build2 without finishing active scope and session on previous build`() {
        lateinit var activeScopeIdFirstBuild: String
        createSimpleAppWithPlugin<CoverageSocketStreams> {
            connectAgent<Build1> { plugUi, build ->
                plugUi.coveragePackages()
                plugUi.activeSessions()!!.run {
                    count shouldBe 0
                    testTypes shouldBe emptySet()
                }
                plugUi.activeScope()

                val startNewSession = StartNewSession(StartPayload("MANUAL")).stringify()
                pluginAction(startNewSession) { status, content ->
                    status shouldBe HttpStatusCode.OK
                    val startSession = content!!.parseJsonData<StartAgentSession>()

                    plugUi.activeSessions()!!.run { count shouldBe 1 }

                    runWithSession(startSession.payload.sessionId) {
                        val gt = build.entryPoint()
                        gt.test1()
                        gt.test2()
                        gt.test3()
                    }
                    pluginAction(StopAgentSession(AgentSessionPayload(startSession.payload.sessionId)).stringify()).join()
                }.join()
                plugUi.activeSessions()!!.count shouldBe 0

                plugUi.activeScope()!!.apply {
                    activeScopeIdFirstBuild = id
                    coverage.percentage shouldBe 100.0
                    coverage.byTestType.find { it.type == "MANUAL" }?.summary?.apply {
                        coverage.percentage shouldBe 100.0
                        testCount shouldBe 1
                        coverage.methodCount.covered shouldBe 4
                    }
                }
                val startNewSession2 = StartNewSession(StartPayload("AUTO")).stringify()
                pluginAction(startNewSession2) { status, content ->
                    status shouldBe HttpStatusCode.OK
                    val startSession2 = content!!.parseJsonData<StartAgentSession>()

                    plugUi.activeSessions()!!.run { count shouldBe 1 }

                    runWithSession(startSession2.payload.sessionId) {
                        val gt = build.entryPoint()
                        gt.test1()
                        gt.test2()
                        gt.test3()
                    }
                }.join()
                delay(100)
            }.reconnect<Build2> { plugUi, _ ->
                plugUi.activeSessions()!!.count shouldBe 0
                plugUi.testsToRun()!!.apply {
                    testTypeToNames shouldBe emptyMap()
                }
                plugUi.activeScope()!!.apply {
                    id shouldNotBe activeScopeIdFirstBuild
                    coverage.percentage shouldBe 0.0
                    coverage.byTestType shouldBe emptyList()
                }
            }
        }
    }

    @Test
    fun `redeploy build1 after finishing active scope and session on this build`() {
        createSimpleAppWithPlugin<CoverageSocketStreams> {
            connectAgent<Build1> { plugUi, build ->
                plugUi.coveragePackages()
                plugUi.activeSessions()!!.run {
                    count shouldBe 0
                    testTypes shouldBe emptySet()
                }

                val startNewSession = StartNewSession(StartPayload("MANUAL")).stringify()
                pluginAction(startNewSession) { status, content ->
                    status shouldBe HttpStatusCode.OK
                    val startSession = content!!.parseJsonData<StartAgentSession>()

                    plugUi.activeSessions()!!.run { count shouldBe 1 }

                    runWithSession(startSession.payload.sessionId) {
                        val gt = build.entryPoint()
                        gt.test1()
                        gt.test2()
                        gt.test3()
                    }

                    pluginAction(StopAgentSession(AgentSessionPayload(startSession.payload.sessionId)).stringify()).join()
                }.join()
                plugUi.activeSessions()!!.count shouldBe 0
                val switchScope = SwitchActiveScope(
                    ActiveScopeChangePayload(
                        scopeName = "new2",
                        savePrevScope = true,
                        prevScopeEnabled = true
                    )
                ).stringify()
                pluginAction(switchScope).join()

                plugUi.coveragePackages()!!.apply {
                    first().coverage shouldBeGreaterThan 0.0
                }
                delay(100)
            }.reconnect<Build1> { plugUi, _ ->
                plugUi.testsToRun()!!.apply {
                    testTypeToNames.isEmpty() shouldBe true
                }
                plugUi.buildCoverage()!!.apply {
                    percentage shouldBe 100.0
                }
                plugUi.activeScope()!!.name shouldBe "new2"
            }
        }
    }
}
