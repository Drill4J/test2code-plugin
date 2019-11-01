package com.epam.drill.plugins.coverage


import com.epam.drill.e2e.*
import com.epam.drill.endpoints.plugin.*
import org.junit.*


class E2eTest : AbstarctE2EPluginTest<CoverageSocketStreams>() {

    @Test(timeout = 10000)
    fun sad() {
        createSimpleAppWithPlugin<CoverageSocketStreams>(true, true) {

            connectAgent(setOf("DrillExtension1.class")) { plugUi, agent ->
                plugUi.subscribe(SubscribeInfo(agentId, buildVersionHash))

                agent.sendEvent(InitInfo(classesCount, "asdad"))
                agent.sendEvent(Initialized())


                val activeScope = plugUi.activeScope()
                plugUi.subscribeOnScope(activeScope!!.id) {
                    println(methods())
                    println(associatedTests())
                    println(coverage())
                    println(coverageByPackages())
                    println(testsUsages())


                    val startSession = StartNewSession(StartPayload("MANUAL")).stringify()

                    val stopSession = StopSession(payload = SessionPayload("xxx")).stringify()


                    pluginAction(startSession)

                    pluginAction(stopSession)

                }
            }.newConnect(setOf("DrillExtension2.class")) { plug, agent ->

                plug.subscribe(SubscribeInfo(agentId, buildVersionHash))

                agent.sendEvent(InitInfo(classesCount, "asdad"))
                agent.sendEvent(Initialized())

                plug.subscribeOnScope(plug.activeScope()!!.id) {
                    println(methods())
                    println(associatedTests())
                    println(coverage())
                    println(coverageByPackages())
                    println(testsUsages())
                }

            }


        }

    }


}