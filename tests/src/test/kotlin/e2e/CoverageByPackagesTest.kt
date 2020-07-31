package com.epam.drill.plugins.test2code.e2e


import com.epam.drill.builds.*
import com.epam.drill.e2e.*
import com.epam.drill.e2e.plugin.*
import com.epam.drill.plugins.test2code.*
import com.epam.drill.plugins.test2code.api.*
import com.epam.drill.plugins.test2code.common.api.*
import io.kotlintest.*
import io.kotlintest.matchers.doubles.*
import io.ktor.http.*
import kotlinx.coroutines.*
import org.junit.jupiter.api.*


class CoverageByPackagesTest : E2EPluginTest() {

    @Test
    fun `cover one method in 2 scopes`() {
        createSimpleAppWithPlugin<CoverageSocketStreams> {
            connectAgent<Build1> { plugUi, build ->

                plugUi.coveragePackages()!!.first().apply {
                    id shouldBe "vsu9sbxes5bl"
                    coveredClassesCount shouldBe 0
                    name shouldBe "com/epam/test"
                    coverage shouldBe 0.0
                    totalClassesCount shouldBe 1
                    assocTestsCount shouldBe 0
                }
                val startNewSession = StartNewSession(StartPayload("MANUAL")).stringify()
                lateinit var cont: String
                pluginAction(startNewSession) { status, content ->
                    status shouldBe HttpStatusCode.OK
                    cont = content!!
                }.join()
                val startSession = cont.parseJsonData<StartSession>()
                println(startSession)
                runWithSession(startSession.payload.sessionId) {
                    val gt = build.entryPoint()
                    gt.test1()
                }


                pluginAction(StopSession(SessionPayload(startSession.payload.sessionId)).stringify()) { status, _ ->
                    status shouldBe HttpStatusCode.OK
                }.join()
                delay(300)//todo move it to core library

                val scopeId = plugUi.activeScope()!!.id
                plugUi.subscribeOnScope(scopeId) {
                    coveragePackages()!!.first().apply {
                        id shouldBe "vsu9sbxes5bl"
                        coveredClassesCount shouldBe 1
                        name shouldBe "com/epam/test"
                        coverage shouldBeGreaterThan 46.6
                        totalClassesCount shouldBe 1
                        classes shouldBe emptyList()
                        assocTestsCount shouldBe 1
                    }
                    plugUi.subscribeOnTest(scopeId, "xxxx:MANUAL") {
                        methodsCoveredByTest()!!.apply {
                            testName shouldBe "xxxx"
                            methodCounts.apply {
                                new shouldBe 0
                                modified shouldBe 0
                                unaffected shouldBe 0
                                all shouldBe 2
                            }
                        }
                    }
                    plugUi.subscribeOnTestType(scopeId, "MANUAL") {
                        methodsCoveredByTestType()!!.apply {
                            testType shouldBe "MANUAL"
                            methodCounts.apply {
                                new shouldBe 0
                                modified shouldBe 0
                                unaffected shouldBe 0
                                all shouldBe 2
                            }
                        }
                    }
                }
                val switchScope = SwitchActiveScope(
                    ActiveScopeChangePayload(
                        scopeName = "new2",
                        savePrevScope = true,
                        prevScopeEnabled = true
                    )
                ).stringify()
                pluginAction(switchScope)
                plugUi.coveragePackages()!!.first().apply {
                    id shouldBe "vsu9sbxes5bl"
                    coveredClassesCount shouldBe 1
                    name shouldBe "com/epam/test"
                    coverage shouldBeGreaterThan 46.6
                    totalClassesCount shouldBe 1
                    assocTestsCount shouldBe 1
                }

                val startNewSession2 = StartNewSession(StartPayload("MANUAL")).stringify()
                pluginAction(startNewSession2) { status, content ->
                    status shouldBe HttpStatusCode.OK
                    val startSession2 = content!!.parseJsonData<StartSession>()
                    runWithSession(startSession2.payload.sessionId) {
                        val gt = build.entryPoint()
                        gt.test1()
                    }

                    pluginAction(StopSession(SessionPayload(startSession2.payload.sessionId)).stringify()) { st, _ ->
                        st shouldBe HttpStatusCode.OK
                    }
                }.join()
                delay(300)//todo move it to core library

                plugUi.subscribeOnScope(plugUi.activeScope()!!.id) {
                    coveragePackages()!!.first().apply {
                        id shouldBe "vsu9sbxes5bl"
                        coveredClassesCount shouldBe 1
                        name shouldBe "com/epam/test"
                        coverage shouldBeGreaterThan 46.6
                        totalClassesCount shouldBe 1
                        assocTestsCount shouldBe 1
                    }
                }
                val switchScope2 = SwitchActiveScope(
                    ActiveScopeChangePayload(
                        scopeName = "new3",
                        savePrevScope = true,
                        prevScopeEnabled = true
                    )
                ).stringify()
                pluginAction(switchScope2)
                plugUi.coveragePackages()!!.first().apply {
                    id shouldBe "vsu9sbxes5bl"
                    coveredClassesCount shouldBe 1
                    name shouldBe "com/epam/test"
                    coverage shouldBeGreaterThan 46.6
                    totalClassesCount shouldBe 1
                    assocTestsCount shouldBe 1
                }
            }
        }
    }
}
