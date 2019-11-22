package e2e

import com.epam.drill.agentmanager.AgentInfoWebSocket
import com.epam.drill.builds.Build1
import com.epam.drill.builds.Build2
import com.epam.drill.common.AgentStatus
import com.epam.drill.e2e.E2EPluginTest
import com.epam.drill.e2e.plugin.runWithSession
import com.epam.drill.e2e.pluginAction
import com.epam.drill.plugins.coverage.*
import io.kotlintest.shouldBe
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import org.junit.jupiter.api.RepeatedTest
import java.util.concurrent.atomic.AtomicInteger

class PluginTest : E2EPluginTest() {

    @RepeatedTest(3) //some stress
    fun testE2ePluginAPI() {
        lateinit var sessionId: String

        createSimpleAppWithPlugin<CoverageSocketStreams>(timeout = 20) {
            val startDownLatch = AtomicInteger(2)
            val finishDownLatch = AtomicInteger(2)

            connectAgent<Build1>("myServiceGroup") { plugUi, build ->
                plugUi.activeSessions()!!.count shouldBe 0
                plugUi.activeScope()!!.coverage shouldBe 0.0
                startDownLatch.await()
                plugUi.activeSessions()!!.count shouldBe 1
                runWithSession(sessionId) {
                    val gt = build.entryPoint()
                    gt.test1()
                }
                finishDownLatch.decrementAndGet()
                plugUi.activeSessions()!!.count shouldBe 0
                plugUi.activeScope()!!.coverage shouldBe 46.666666666666664
            }
            connectAgent<Build2>("myServiceGroup") { plugUi, build ->
                plugUi.activeSessions()!!.count shouldBe 0
                plugUi.activeScope()!!.coverage shouldBe 0.0
                startDownLatch.await()
                plugUi.activeSessions()!!.count shouldBe 1
                while (finishDownLatch.get() > 1) delay(100)
                runWithSession(sessionId) {
                    val gt = build.entryPoint()
                    gt.test2()
                }
                finishDownLatch.decrementAndGet()
                plugUi.activeSessions()!!.count shouldBe 0
                plugUi.activeScope()!!.coverage shouldBe 33.333333333333336
            }

            uiWatcher { ch ->
                waitForMultipleAgents(ch)
                val payload = StartPayload("MANUAL")
                val startNewSession1 = StartNewSession(payload)
                val startNewSession = startNewSession1.stringify()
                val (status, content) = pluginAction(startNewSession, "myServiceGroup", "test-to-code-mapping")
                val startSession = commonSerDe.parse(commonSerDe.actionSerializer, content!!) as StartSession
                sessionId = startSession.payload.sessionId
                startDownLatch.decrementAndGet()
                startDownLatch.decrementAndGet()
                status shouldBe HttpStatusCode.OK
                finishDownLatch.await()
                pluginAction(
                    StopSession(SessionPayload(sessionId)).stringify(),
                    "myServiceGroup",
                    "test-to-code-mapping"
                )
            }
        }

    }

    private suspend fun waitForMultipleAgents(ch: Channel<Set<AgentInfoWebSocket>>) {
        lateinit var message: Set<AgentInfoWebSocket>
        do {
            message = ch.receive()
            if (message.all { it.activePluginsCount == 1 } && message.all { it.status == AgentStatus.ONLINE }) break
        } while (true)
    }


}

suspend fun AtomicInteger.await() {
    while (this.get() != 0) delay(100)
}