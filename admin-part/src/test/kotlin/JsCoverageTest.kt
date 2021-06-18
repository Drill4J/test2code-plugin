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

import com.epam.drill.plugin.api.*
import com.epam.drill.plugins.test2code.api.*
import com.epam.drill.plugins.test2code.test.js.*
import com.epam.drill.plugins.test2code.util.*
import com.epam.kodux.*
import jetbrains.exodus.entitystore.*
import kotlinx.coroutines.*
import java.io.*
import java.util.*
import kotlin.test.*

class JsCoverageTest {

    private val storageDir = File("build/tmp/test/storages/${this::class.simpleName}-${UUID.randomUUID()}")

    private val storeClient = StoreClient(PersistentEntityStores.newInstance(storageDir))

    private val emptyRuntimeConfig = RuntimeConfig("")

    @AfterTest
    fun cleanStore() {
        storeClient.close()
        storageDir.deleteRecursively()
    }

    @Test
    fun `coverageData for active scope with custom js probes`() = runBlocking {
        val adminData = object : AdminData {
            override suspend fun loadClassBytes(): Map<String, ByteArray> = emptyMap()
        }
        val state = AgentState(
            storeClient, jsAgentInfo, adminData, emptyRuntimeConfig
        )
        state.init()
        (state.data as DataBuilder) += ast
        state.initialized()
        val active = state.activeScope
        active.execSession("MANUAL") { sessionId ->
            addProbes(sessionId) { probes }
        }
        active.execSession("AUTO") { sessionId ->
            addProbes(sessionId) { IncorrectProbes.underCount }
            addProbes(sessionId) { IncorrectProbes.overCount }
            addProbes(sessionId) { IncorrectProbes.notExisting }
        }
        val finished = active.finish(enabled = true)
        val context = state.coverContext()
        val bundleCounters = finished.calcBundleCounters(context, emptyMap())
        val coverageData = bundleCounters.calculateCoverageData(context)
        coverageData.run {
            assertEquals(Count(3, 5).toDto(), coverage.count.toDto())
            assertEquals(listOf("foo/bar"), packageCoverage.map { it.name })
            assertEquals(1, packageCoverage[0].coveredClassesCount)
            assertEquals(1, packageCoverage[0].totalClassesCount)
            assertEquals(2, packageCoverage[0].coveredMethodsCount)
            assertEquals(3, packageCoverage[0].totalMethodsCount)
            packageCoverage[0].classes.run {
                assertEquals(listOf("foo/bar"), map { it.path })
                assertEquals(listOf("baz.js"), map { it.name })
                assertEquals(listOf(100.0, 0.0, 50.0), flatMap { it.methods }.map { it.coverage })
            }
            assertEquals(
                setOf(TypedTest("default", "MANUAL"), TypedTest("default", "AUTO")),
                associatedTests.flatMap { it.tests }.toSet()
            )
            buildMethods.run {
                assertEquals(2, totalMethods.coveredCount)
                assertEquals(3, totalMethods.totalCount)
            }

        }
    }

    private fun ActiveScope.execSession(testType: String, block: ActiveScope.(String) -> Unit) {
        val sessionId = genUuid()
        startSession(sessionId, testType)
        block(sessionId)
        finishSession(sessionId)
    }
}
