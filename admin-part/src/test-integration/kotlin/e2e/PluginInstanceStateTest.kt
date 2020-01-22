package com.epam.drill.plugins.test2code.e2e

import com.epam.drill.builds.Build1
import com.epam.drill.builds.Build2
import com.epam.drill.e2e.E2EPluginTest
import com.epam.drill.e2e.plugin.runWithSession
import com.epam.drill.admin.endpoints.plugin.SubscribeInfo
import com.epam.drill.plugins.test2code.*
import io.kotlintest.matchers.boolean.shouldBeTrue
import io.kotlintest.matchers.doubles.shouldBeGreaterThan
import io.kotlintest.shouldBe
import io.kotlintest.shouldNotBe
import io.ktor.http.HttpStatusCode
import org.junit.jupiter.api.Test

class PluginInstanceStateTest : E2EPluginTest() {

    @Test
    fun `Deploy build2 with finishing active scope and session on previous build`() {
        createSimpleAppWithPlugin<CoverageSocketStreams> {
            connectAgent<Build1> { plugUi, build ->
                plugUi.coverageByPackages()
                plugUi.activeSessions()!!.run {
                    count shouldBe 0
                    testTypes shouldBe emptySet()
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
                val switchScope = SwitchActiveScope(
                    ActiveScopeChangePayload(
                        scopeName = "new2",
                        savePrevScope = true,
                        prevScopeEnabled = true
                    )
                ).stringify()
                pluginAction(switchScope)

                plugUi.coverageByPackages()!!.apply {
                    first().coverage shouldBeGreaterThan 0.0
                }

            }.reconnect<Build2> { plugUi, _ ->
                plugUi.testsToRun()!!.apply {
                    testTypeToNames.isNotEmpty() shouldBe true
                }
                plugUi.buildCoverage()!!.apply {
                    arrow shouldBe ArrowType.DECREASE
                    diff shouldNotBe 0.0
                    previousBuildInfo.apply {
                        first shouldBe "30507"
                        second shouldBe ""
                    }
                }
            }
        }
    }

    @Test
    fun `Deploy build2 without finishing active scope and session on previous build`() {
        lateinit var activeScopeIdFirstBuild: String
        createSimpleAppWithPlugin<CoverageSocketStreams> {
            connectAgent<Build1> { plugUi, build ->
                plugUi.coverageByPackages()
                plugUi.activeSessions()!!.run {
                    count shouldBe 0
                    testTypes shouldBe emptySet()
                }
                plugUi.activeScope()

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

                plugUi.activeScope()!!.apply {
                    activeScopeIdFirstBuild = id
                    coverage shouldBe 100.0
                    coveragesByType.getValue("MANUAL").apply {
                        testType shouldBe "MANUAL"
                        coverage shouldBe 100.0
                        testCount shouldBe 1
                        coveredMethodsCount shouldBe 4
                    }
                }
                val startNewSession2 = StartNewSession(StartPayload("AUTO")).stringify()
                pluginAction(startNewSession2) { status, content ->
                    status shouldBe HttpStatusCode.OK
                    val startSession2 = commonSerDe.parse(commonSerDe.actionSerializer, content!!) as StartSession

                    plugUi.activeSessions()!!.run { count shouldBe 1 }

                    runWithSession(startSession2.payload.sessionId) {
                        val gt = build.entryPoint()
                        gt.test1()
                        gt.test2()
                        gt.test3()
                    }
                }.join()
            }.reconnect<Build2> { plugUi, _ ->
                plugUi.activeSessions()!!.count shouldBe 0
                plugUi.testsToRun()!!.apply {
                    testTypeToNames.isEmpty().shouldBeTrue()
                }
                plugUi.buildCoverage()!!.apply {
                    arrow shouldBe null
                    diff shouldBe 0.0
                }
                plugUi.activeScope()!!.apply {
                    id shouldNotBe activeScopeIdFirstBuild
                    coverage shouldBe 0.0
                    coveragesByType shouldBe emptyMap()
                }
            }
        }
    }

    @Test
    fun `Redeploy build1 after finishing active scope and session on this build`() {
        createSimpleAppWithPlugin<CoverageSocketStreams> {
            connectAgent<Build1> { plugUi, build ->
                plugUi.coverageByPackages()
                plugUi.activeSessions()!!.run {
                    count shouldBe 0
                    testTypes shouldBe emptySet()
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
                val switchScope = SwitchActiveScope(
                    ActiveScopeChangePayload(
                        scopeName = "new2",
                        savePrevScope = true,
                        prevScopeEnabled = true
                    )
                ).stringify()
                pluginAction(switchScope).join()

                plugUi.coverageByPackages()!!.apply {
                    first().coverage shouldBeGreaterThan 0.0
                }

            }.reconnect<Build1> { plugUi, _ ->
                plugUi.testsToRun()!!.apply {
                    testTypeToNames.isEmpty() shouldBe true
                }
                plugUi.buildCoverage()!!.apply {
                    coverage shouldBe 100.0
                    arrow shouldBe null
                    diff shouldNotBe 0.0
                    previousBuildInfo.apply {
                        first shouldBe ""
                        second shouldBe ""
                    }
                }
                plugUi.activeScope()!!.name shouldBe "New Scope 1"
            }
        }
    }

    @Test
    fun `Deploy build2 and check state of build1`() {
        createSimpleAppWithPlugin<CoverageSocketStreams> {
            connectAgent<Build1> { plugUi, build ->
                plugUi.risks()!!.apply {
                    newMethods.count() shouldBe 4
                    modifiedMethods.count() shouldBe 0
                }
                plugUi.coverageByPackages()
                plugUi.activeSessions()!!.run {
                    count shouldBe 0
                    testTypes shouldBe emptySet()
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

                    pluginAction(StopSession(SessionPayload(startSession.payload.sessionId)).stringify())
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

                plugUi.risks()!!.apply {
                    newMethods.count() shouldBe 0
                    modifiedMethods.count() shouldBe 0
                }

            }.reconnect<Build2> { plugUi, _ ->
                plugUi.subscribe(SubscribeInfo(agentId, "30507"))
                plugUi.buildCoverage()!!.apply {
                    coverage shouldBe 100.0
                    arrow shouldBe null
                    diff shouldBe 100.0
                }

                plugUi.risks()!!.apply {
                    newMethods.count() shouldBe 0
                    modifiedMethods.count() shouldBe 0
                }

                plugUi.activeScope() shouldBe null
            }
        }
    }
}
