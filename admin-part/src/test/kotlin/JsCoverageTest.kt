package com.epam.drill.plugins.test2code

import com.epam.drill.common.*
import com.epam.drill.plugin.api.*
import com.epam.drill.plugins.test2code.api.*
import com.epam.drill.plugins.test2code.common.api.*
import com.epam.drill.plugins.test2code.test.js.*
import com.epam.kodux.*
import jetbrains.exodus.entitystore.*
import kotlinx.coroutines.*
import java.io.*
import java.util.*
import kotlin.test.*

class JsCoverageTest {

    private val storageDir = File("build/tmp/test/storages/${this::class.simpleName}-${UUID.randomUUID()}")

    private val storeClient = StoreClient(PersistentEntityStores.newInstance(storageDir))

    @AfterTest
    fun cleanStore() {
        storeClient.store.close()
        storageDir.deleteRecursively()
    }

    @Test
    fun `coverageData for active scope with custom js probes`() = runBlocking {
        val buildManager = buildManager()
        val state = PluginInstanceState(
            storeClient, jsAgentInfo, buildManager
        )
        state.init()
        (state.data as DataBuilder) += ast
        state.initialized()
        val active = state.activeScope
        active.test(probes)
        active.test(probes)
        val finished = active.finish(enabled = true)
        val coverageData = finished.calculateCoverageData(state, jsAgentInfo.buildVersion, 1)
        coverageData.run {
            assertEquals(Count(3, 5), coverage.count)
            assertEquals(listOf("foo/bar"), packageCoverage.map { it.name })
            packageCoverage[0].run {
                assertEquals(listOf("foo/bar"), classes.map { it.path })
                assertEquals(listOf("baz.js"), classes.map { it.name })
                assertEquals(listOf(50.0, 100.0, 50.0), classes.flatMap { it.methods }.map { it.coverage })
                assertEquals(60.0, coverage)
            }
            assertEquals(
                setOf(TypedTest("default", "MANUAL")),
                associatedTests.flatMap { it.tests }.toSet()
            )
        }
    }

    private fun buildManager(): BuildManager {
        return object : BuildManager {
            val info = BuildInfo(jsAgentInfo.buildVersion).let { mapOf(it.version to it) }

            override val builds = info.values

            override fun get(version: String) = info[version]

        }
    }

    private fun ActiveScope.test(probes: List<ExecClassData>) {
        val sessionId = genUuid()
        startSession(sessionId, "MANUAL")
        addProbes(sessionId, probes)
        finishSession(sessionId) {}
    }
}
