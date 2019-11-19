package com.epam.drill.plugins.coverage.e2e

import com.epam.drill.builds.*
import com.epam.drill.e2e.*
import com.epam.drill.e2e.plugin.*
import com.epam.drill.endpoints.plugin.*
import com.epam.drill.plugins.coverage.*
import io.kotlintest.*
import io.kotlintest.matchers.boolean.*
import io.kotlintest.matchers.doubles.*
import io.ktor.http.*
import org.junit.*
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
                    testsToRun.isNotEmpty() shouldBe true
                }
                plugUi.buildCoverage()!!.apply {
                    arrow shouldBe ArrowType.DECREASE
                    diff shouldNotBe 0.0
                    previousBuildInfo.apply {
                        first shouldBe "30507"
                        second shouldBe "sad"
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

                plugUi.activeScope()!!.apply {
                    activeScopeIdFirstBuild = id
                    coverage shouldBe 80.0
                    coveragesByType.getValue("MANUAL").apply {
                        testType shouldBe "MANUAL"
                        coverage shouldBe 80.0
                        testCount shouldBe 1
                        coveredMethodsCount shouldBe 3
                    }
                }
                val startNewSession2 = StartNewSession(StartPayload("AUTO")).stringify()
                val (status2, content2) = pluginAction(startNewSession2)
                status2 shouldBe HttpStatusCode.OK
                val startSession2 = commonSerDe.parse(commonSerDe.actionSerializer, content2!!) as StartSession

                plugUi.activeSessions()!!.run { count shouldBe 1 }

                runWithSession(startSession2.payload.sessionId) {
                    val gt = build.entryPoint()
                    gt.test1()
                    gt.test2()
                    gt.test3()
                }
            }.reconnect<Build2> { plugUi, _ ->
                plugUi.activeSessions()!!.count shouldBe 0
                plugUi.testsToRun()!!.apply {
                    testsToRun.isEmpty().shouldBeTrue()
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

            }.reconnect<Build1> { plugUi, _ ->
                plugUi.testsToRun()!!.apply {
                    testsToRun.isEmpty() shouldBe true
                }
                plugUi.buildCoverage()!!.apply {
                    coverage shouldBe 80.0
                    arrow shouldBe ArrowType.INCREASE
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

    //TODO: enable the test after admin is fixed
    @Test
    @Ignore
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
                val switchScope = SwitchActiveScope(
                    ActiveScopeChangePayload(
                        scopeName = "new2",
                        savePrevScope = true,
                        prevScopeEnabled = true
                    )
                ).stringify()
                pluginAction(switchScope)

                plugUi.risks()!!.apply {
                    newMethods.count() shouldBe 1
                    modifiedMethods.count() shouldBe 0
                }

            }.reconnect<Build2> { plugUi, _ ->
                plugUi.subscribe(SubscribeInfo(agentId, "30507"))
                plugUi.buildCoverage()!!.apply {
                    coverage shouldBe 80.0
                    //TODO arrow must be null for first build in system. To edit it after fix issue.
                    arrow shouldBe ArrowType.INCREASE
                    diff shouldBe 80.0
                }

                plugUi.risks()!!.apply {
                    newMethods.count() shouldBe 1
                    modifiedMethods.count() shouldBe 0
                }

                plugUi.activeScope() shouldBe null
            }
        }
    }
}