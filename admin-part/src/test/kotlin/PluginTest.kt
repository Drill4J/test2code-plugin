/**
 * Copyright 2020 EPAM Systems
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.epam.drill.plugins.test2code

import com.epam.drill.common.*
import com.epam.drill.plugin.api.*
import com.epam.drill.plugin.api.end.*
import com.epam.drill.plugins.test2code.api.*
import com.epam.drill.plugins.test2code.common.api.*
import com.epam.drill.plugins.test2code.storage.*
import com.epam.drill.plugins.test2code.util.*
import com.epam.kodux.*
import jetbrains.exodus.entitystore.*
import kotlinx.coroutines.*
import java.io.*
import java.util.*
import kotlin.random.Random.*
import kotlin.random.Random.Default.nextInt
import kotlin.test.*
import kotlin.time.*


class PluginTest {

    private val storageDir = File("build/tmp/test/storages/${this::class.simpleName}-${UUID.randomUUID()}")

    private val storeClient = StoreClient(PersistentEntityStores.newInstance(storageDir))

    private val agentInfo = AgentInfo(
        id = "ag",
        name = "ag",
        description = "",
        buildVersion = "0.1.0",
        agentType = "JAVA",
        agentVersion = "0.1.0"
    )

    private val sender = EmptySender

    private val adminData = object : AdminData {
        override val classBytes = emptyMap<String, ByteArray>()
    }

    @AfterTest
    fun cleanStore() {
        storeClient.close()
        storageDir.deleteRecursively()
    }

    private suspend fun initPlugin(
        buildVersion: String,
    ): Plugin = Plugin(
        adminData,
        sender,
        storeClient,
        agentInfo.copy(buildVersion = buildVersion),
        "test2code"
    ).apply {
        ClassData(buildVersion).store(storeClient)
        initialize()
        return this
    }

    @Test
    fun `should start & finish session and collect coverage`() = runBlocking {
        val plugin: Plugin = initPlugin("0.1.0")
        plugin.state.initialized()
        val finishedSession = finishedSession(plugin, "sessionId", 1, 3, 5)
        assertEquals(3, finishedSession?.probes?.size)
    }

    /**
     * when countAddProbes = 100 OutOfMemoryError @link [com.epam.drill.plugins.test2code.storage.storeSession]
     */
    @Test
    fun `perf test! should start & finish session and collect coverage`() = runBlocking {
        val plugin: Plugin = initPlugin("0.1.0")
        plugin.state.initialized()
        val finishedSession = finishedSession(plugin, "sessionId", 30)
        assertEquals(3000, finishedSession?.probes?.size)
    }

    @Test
    fun `should finish scope with 2 session and takes probes`() = runBlocking {
        switchScopeWithProbes()
    }

    @Test
    fun `perf check! should finish scope with 2 session and takes probes`() = runBlocking {
        switchScopeWithProbes(20)
    }

    private suspend fun switchScopeWithProbes(countAddProbes: Int = 1) {
        val buildVersion = "0.1.0"
        val plugin: Plugin = initPlugin(buildVersion)
        plugin.state.initialized()
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
            sessionId,
            "MANUAL"
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
        println("finished session with size probes = ${finishedSession?.probes?.size}")
        return finishedSession
    }

    private fun addProbes(
        plugin: Plugin,
        sessionId: String,
        countAddProbes: Int = 1_000,
        sizeExec: Int = 100_000,
        sizeProbes: Int = 100_000_000,
    ) {
        repeat(countAddProbes) { index ->
            index.takeIf { it % 10 == 0 }?.let { println("adding probes, index = $it...") }
            val execClassData = listOf(0 until sizeExec).flatten().map {
                ExecClassData(
                    id = Default.nextLong(100_000_000),
                    className = "foo/Bar",
                    probes = randomBoolean(sizeProbes).toBitSet()
                )
            }
            plugin.activeScope.addProbes(sessionId) { execClassData }
        }
    }

    private fun randomBoolean(n: Int = 100) = listOf(0 until n).flatten().map { true }

    @Test
    fun `cannot toggleBaseline initial build`() = runBlocking {
        val plugin = initPlugin("0.1.0")

        assertEquals(StatusCodes.BAD_REQUEST, plugin.toggleBaseline().code)
    }

    @Test
    fun `toggleBaseline second build`() = runBlocking {
        val version = "0.1.0"
        initPlugin(version)

        val plugin2 = initPlugin("0.2.0")

        assertEquals(version, plugin2.state.coverContext().parentBuild?.version)
        assertEquals(StatusCodes.OK, plugin2.toggleBaseline().code)
        assertEquals(StatusCodes.OK, plugin2.toggleBaseline().code)
    }

    @Test
    fun `cannot toggle initial build after redeploy it`() = runBlocking {
        val plugin = initPlugin("0.1.0")

        initPlugin("0.2.0")

        plugin.initialize()
        assertEquals(StatusCodes.BAD_REQUEST, plugin.toggleBaseline().code)
    }

    @Test
    fun `when redeploy current build it should compare with parent baseline`() = runBlocking {
        val version1 = "0.1.0"
        initPlugin(version1)

        val plugin2 = initPlugin("0.2.0")
        assertEquals(StatusCodes.OK, plugin2.toggleBaseline().code)

        plugin2.initialize()
        assertEquals(version1, plugin2.state.coverContext().parentBuild?.version)
    }

    @Test
    fun `when redeploy stored build - compare it with new baseline and there is able to toggle`() = runBlocking {
        val plugin = initPlugin("0.1.0")

        val version2 = "0.2.0"
        val plugin2 = initPlugin(version2)
        assertEquals(StatusCodes.OK, plugin2.toggleBaseline().code)

        plugin.initialize()
        assertEquals(version2, plugin.state.coverContext().parentBuild?.version)
        assertEquals(StatusCodes.OK, plugin.toggleBaseline().code)
    }

    @Test
    fun `when redeploy stored build with new baseline it will be recalculated with it`() = runBlocking {
        val version1 = "0.1.0"
        initPlugin(version1)

        val version2 = "0.2.0"
        val plugin2 = initPlugin(version2)
        assertEquals(StatusCodes.OK, plugin2.toggleBaseline().code)

        val plugin3 = initPlugin("0.3.0")
        assertEquals(version2, plugin3.state.coverContext().parentBuild?.version)

        plugin2.initialize()
        assertEquals(StatusCodes.OK, plugin2.toggleBaseline().code)

        plugin3.initialize()
        assertEquals(version1, plugin3.state.coverContext().parentBuild?.version)
    }

    @Test
    fun `for the first and second build testsToRunSummaries will be empty`() = runBlocking {
        val version1 = "0.1.0"
        val plugin = initPlugin(version1)
        assertTrue(
            plugin.storeClient.loadTestsToRunSummary(
                buildVersion = version1
            ).isEmpty()
        )

        val version2 = "0.2.0"
        val plugin2 = initPlugin(version2)
        assertTrue(
            plugin2.storeClient.loadTestsToRunSummary(
                buildVersion = version2,
                parentVersion = version1
            ).isEmpty()
        )
    }

    @Test
    fun `for the third build testsToRunSummaries will have one element`() = runBlocking {
        val version1 = "0.1.0"
        initPlugin(version1)
        val version2 = "0.2.0"
        initPlugin(version2)

        val currentVersion = "0.3.0"
        val plugin3 = initPlugin(currentVersion)
        val testsToRunSummary = plugin3.storeClient.loadTestsToRunSummary(
            buildVersion = currentVersion,
            parentVersion = version1
        )
        assertEquals(1, testsToRunSummary.size)
        assertEquals(version2, testsToRunSummary.first().buildVersion)
        assertEquals(version1, testsToRunSummary.first().parentVersion)
    }

    @Test
    fun `testsToRunSummaries should be sorted by timestamp`() = runBlocking {
        val version1 = "0.1.0"
        initPlugin(version1)
        initPlugin("0.2.0")
        initPlugin("0.3.0")
        val currentVersion = "0.4.0"
        val plugin4 = initPlugin(currentVersion)

        val testsToRunSummaries = plugin4.storeClient.loadTestsToRunSummary(
            buildVersion = currentVersion,
            parentVersion = version1
        )
        assertEquals(2, testsToRunSummaries.size)
        assertTrue(testsToRunSummaries.first().lastModifiedAt < testsToRunSummaries.last().lastModifiedAt)
    }

    data class Xs(val covered:Int, val total:Int)

    @Test
    fun xx() {

        val times = Int.MAX_VALUE
        measureTimedValue {
            repeat(times) {
                Xs(nextInt(), nextInt())
            }
        }.duration

        println(
            measureTimedValue {
                repeat(times) {
                    Xs(nextInt(), nextInt())
                }
            }.duration
        )

        println(
            measureTimedValue {
                repeat(times) {
                    Count(nextInt(), nextInt())
                }
            }.duration
        )
    }


}

private object EmptySender : Sender {
    override suspend fun send(context: SendContext, destination: Any, message: Any) {}

}
