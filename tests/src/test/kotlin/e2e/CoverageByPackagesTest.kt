package com.epam.drill.plugins.test2code.e2e


import com.epam.drill.builds.*
import com.epam.drill.e2e.*
import com.epam.drill.e2e.plugin.*
import com.epam.drill.plugins.test2code.*
import io.kotlintest.*
import io.ktor.http.*
import kotlinx.coroutines.*
import org.junit.jupiter.api.*


class CoverageByPackagesTest : E2EPluginTest() {

    @Test
    fun `cover one method in 2 scopes`() {
        createSimpleAppWithPlugin<CoverageSocketStreams> {
            connectAgent<Build1> { plugUi, build ->

                plugUi.coverageByPackages()!!.first().apply {
                    id shouldBe "vsu9sbxes5bl"
                    coveredClassesCount shouldBe 0
                    name shouldBe "com/epam/test"
                    coverage shouldBe 0.0
                    totalClassesCount shouldBe 1
                    classes.size shouldNotBe 0
                    assocTestsCount shouldBe null
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

                plugUi.methodsCoveredByTest()
                plugUi.methodsCoveredByTestType()

                plugUi.subscribeOnScope(plugUi.activeScope()!!.id) {
                    coverageByPackages()!!.first().apply {
                        id shouldBe "vsu9sbxes5bl"
                        coveredClassesCount shouldBe 1
                        name shouldBe "com/epam/test"
                        coverage shouldBe 46.666666666666664
                        totalClassesCount shouldBe 1
                        classes.size shouldNotBe 0
                        assocTestsCount shouldBe 1
                    }
                    methodsCoveredByTest()!!.first().apply {
                        testName shouldBe "xxxx"
                        newMethods.size shouldBe 2
                        modifiedMethods.size shouldBe 0
                        unaffectedMethods.size shouldBe 0
                    }
                    methodsCoveredByTestType()!!.first().apply {
                        testType shouldBe "MANUAL"
                        newMethods.size shouldBe 2
                        modifiedMethods.size shouldBe 0
                        unaffectedMethods.size shouldBe 0
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
                plugUi.coverageByPackages()!!.first().apply {
                    id shouldBe "vsu9sbxes5bl"
                    coveredClassesCount shouldBe 1
                    name shouldBe "com/epam/test"
                    coverage shouldBe 46.666666666666664
                    totalClassesCount shouldBe 1
                    classes.size shouldNotBe 0
                    assocTestsCount shouldBe 1
                }

                val methodsCoveredByTest = plugUi.methodsCoveredByTest()!!
                val methodsCoveredByTestType = plugUi.methodsCoveredByTestType()!!
                methodsCoveredByTest.size shouldBe 1
                methodsCoveredByTestType.size shouldBe 1
                methodsCoveredByTest.first().apply {
                    testName shouldBe "xxxx"
                    newMethods.size shouldBe 2
                    modifiedMethods.size shouldBe 0
                    unaffectedMethods.size shouldBe 0
                }
                methodsCoveredByTestType.first().apply {
                    testType shouldBe "MANUAL"
                    newMethods.size shouldBe 2
                    modifiedMethods.size shouldBe 0
                    unaffectedMethods.size shouldBe 0
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
                    coverageByPackages()!!.first().apply {
                        id shouldBe "vsu9sbxes5bl"
                        coveredClassesCount shouldBe 1
                        name shouldBe "com/epam/test"
                        coverage shouldBe 46.666666666666664
                        totalClassesCount shouldBe 1
                        classes.size shouldNotBe 0
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
                plugUi.coverageByPackages()!!.first().apply {
                    id shouldBe "vsu9sbxes5bl"
                    coveredClassesCount shouldBe 1
                    name shouldBe "com/epam/test"
                    coverage shouldBe 46.666666666666664
                    totalClassesCount shouldBe 1
                    classes.size shouldNotBe 0
                    assocTestsCount shouldBe 1
                }
            }
        }
    }
}
