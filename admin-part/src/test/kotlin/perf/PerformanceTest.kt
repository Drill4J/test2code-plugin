package com.epam.drill.plugins.test2code.perf

import com.epam.drill.plugins.test2code.*
import com.epam.drill.plugins.test2code.api.*
import com.epam.drill.plugins.test2code.common.api.*
import kotlinx.coroutines.*
import kotlin.random.*
import kotlin.test.*

class PerformanceTest : PluginTest() {

    @Test
    fun `should start & finish session and collect coverage`() = runBlocking {
        val plugin: Plugin = initPlugin("0.1.0")
        val finishedSession = finishedSession(plugin, "sessionId", 1, 3)
        plugin.state.close()
        assertEquals(3, finishedSession?.probes?.size)
    }

    /**
     * when countAddProbes = 100 OutOfMemoryError @link [com.epam.drill.plugins.test2code.storage.storeSession]
     */
    @Test
    fun `perf test! should start & finish session and collect coverage`() = runBlocking {
        val plugin: Plugin = initPlugin("0.1.0")
        val finishedSession = finishedSession(plugin, "sessionId", 30)
        plugin.state.close()
        assertEquals(3000, finishedSession?.probes?.size)
    }

    @Test
    fun `should finish scope with 2 session and takes probes`() = runBlocking {
        switchScopeWithProbes()
    }

    @Test
    fun `perf check! should finish scope with 2 session and takes probes`() = runBlocking {
        switchScopeWithProbes(800)
    }

    private suspend fun switchScopeWithProbes(countAddProbes: Int = 1) {
        val buildVersion = "0.1.0"
        val plugin: Plugin = initPlugin(buildVersion)
        finishedSession(plugin, "sessionId", countAddProbes)
        finishedSession(plugin, "sessionId2", countAddProbes)
        val res = plugin.changeActiveScope(ActiveScopeChangePayload("new scope", true))
        val scopes = plugin.state.scopeManager.run {
            byVersion(buildVersion, withData = true)
        }
        assertEquals(200, res.code)
        assertEquals(2, scopes.first().data.sessions.size)
    }

    private suspend fun finishedSession(
        plugin: Plugin,
        sessionId: String,
        countAddProbes: Int = 1,
        sizeExec: Int = 100,
        sizeProbes: Int = 10_000,
    ): FinishedSession? {
        plugin.activeScope.startSession(
            sessionId = sessionId,
            testType = "MANUAL",
        )
        addProbes(
            plugin,
            sessionId,
            countAddProbes = countAddProbes,
            sizeExec = sizeExec,
            sizeProbes = sizeProbes
        )
        println("it has added probes, starting finish session...")
        val finishedSession = plugin.state.finishSession(sessionId)
        println("finished session with size peerobes = ${finishedSession?.probes?.size}")
        return finishedSession
    }

    private fun addProbes(
        plugin: Plugin,
        sessionId: String,
        countAddProbes: Int,
        sizeExec: Int,
        sizeProbes: Int,
    ) {
        repeat(countAddProbes) { index ->
            index.takeIf { it % 10 == 0 }?.let { println("adding probes, index = $it...") }
            val execClassData = (0 until sizeExec).map {
                ExecClassData(
                    className = "foo/Bar/$index/$it",
                    probes = randomBoolean(sizeProbes),
                    testName = "$index/$it"
                )
            }
            plugin.activeScope.addProbes(sessionId) { execClassData }
        }
    }

    private fun randomBoolean(n: Int = 100) = (0 until n).map { Random.nextBoolean() }.toBitSet()


//    @Test
//    fun `performance test`() = runBlocking {
//        val plugin: Plugin = initPlugin("0.1.0")
//        plugin
//    }


}
