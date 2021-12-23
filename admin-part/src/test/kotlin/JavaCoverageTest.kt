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
import com.epam.drill.plugins.test2code.common.api.*
import com.epam.drill.plugins.test2code.coverage.*
import com.epam.drill.plugins.test2code.storage.*
import com.epam.drill.plugins.test2code.test.java.*
import com.epam.drill.plugins.test2code.util.*
import kotlinx.coroutines.*
import kotlin.test.*

class JavaCoverageTest : PluginTest() {

    companion object {
        const val AUTO_TEST_TYPE = "AUTO"
    }

    @Test
    fun `coverageData for active scope with manual`() = runBlocking {
        val sessionId = genUuid()
        val (coverageData, _) = calculateCoverage(sessionId) {
            this.execSession("test", sessionId, "MANUAL") { sessionId ->
                addProbes(sessionId) { manualFullProbes }
            }
        }
        coverageData.run {
            println(coverage)
            assertEquals(1, coverage.classCount.total)
            assertTrue(coverage.percentage > 0.0)
            println(packageCoverage)
            println(packageCoverage[0])
            println(associatedTests)
            println(buildMethods)
        }
    }

    @Test
    fun `coverageData for active scope with auto session and filter results`() {
        runBlocking {
            val (coverageData, context) = runAutoSession(passedTest = "test1")
            coverageData.run {
                assertEquals(1, coverage.classCount.total)
                assertTrue(coverage.percentage > 0.0)
            }

            filterAndCalculateCoverage(
                context,
                filter = FieldFilter("result", value = "PASSED"),
                expectedProbes = autoProbesWithPartCoverage,
                expectedCoverage = 25.0
            )
        }
    }

    @Test
    fun `coverageData for active scope with auto and complex filter`() {
        runBlocking {
            val (coverageData, context) = runAutoSession(passedTest = "test1")
            coverageData.run {
                assertEquals(1, coverage.classCount.total)
                assertTrue(coverage.percentage > 0.0)
            }

            filterAndCalculateCoverage(
                context,
                filter = FieldFilter("typedTest->name", value = "test2"),
                expectedProbes = autoProbesWithFullCoverage,
                expectedCoverage = 100.0
            )
        }
    }

    @Test
    fun `coverageData for active scope with auto and filter with null value`() {
        runBlocking {
            val (coverageData, context) = runAutoSession(passedTest = "test1")
            coverageData.run {
                assertEquals(1, coverage.classCount.total)
                assertTrue(coverage.percentage > 0.0)
            }

            filterAndCalculateCoverage(
                context,
                filter = FieldFilter("details->engine", value = ""),
                expectedProbes = emptyList(),
                expectedCoverage = 0.0,
            )
        }
    }

    private suspend fun runAutoSession(
        sessionId: String = genUuid(),
        passedTest: String,
    ) = calculateCoverage(sessionId) {
        val session: ActiveSession? = startSession(sessionId = sessionId, testType = AUTO_TEST_TYPE)
        this.execSession(
            testName = passedTest,
            sessionId = sessionId,
            testType = AUTO_TEST_TYPE,
            session,
        ) { sessionId ->
            addProbes(sessionId) { autoProbesWithPartCoverage }
        }
        this.execSession(
            testName = "test2",
            sessionId = sessionId,
            testType = AUTO_TEST_TYPE,
            session,
        ) { sessionId ->
            addProbes(sessionId) { autoProbesWithFullCoverage }
        }
    }

    private val classEmptyBody = object : AdminData {
        override suspend fun loadClassBytes(): Map<String, ByteArray> = classBytesEmptyBody
        override suspend fun loadClassBytes(buildVersion: String): Map<String, ByteArray> = classBytesEmptyBody
    }

    private suspend fun calculateCoverage(
        sessionId: String,
        addProbes: suspend ActiveScope.() -> Unit
    ): Pair<CoverageInfoSet, CoverContext> {
        val plugin = initPlugin("0.1.0", classEmptyBody)
        plugin.initialize()
        val state = plugin.state

        val activeScope = state.activeScope
        activeScope.addProbes()
        state.finishSession(sessionId)
        val finishedScope = activeScope.finish(enabled = true)

        val context = state.coverContext()
        val bundleCounters = finishedScope.calcBundleCounters(context, classBytesEmptyBody)
        return bundleCounters.calculateCoverageData(context) to context
    }

    private suspend fun ActiveScope.execSession(
        testName: String,
        sessionId: String,
        testType: String,
        session: ActiveSession? = null,
        result: TestResult = TestResult.PASSED,
        block: suspend ActiveScope.(String) -> Unit
    ) {
        val startSessionNew = session ?: startSession(sessionId = sessionId, testType = testType)
        block(sessionId)
        startSessionNew?.addTests(
            listOf(
                TestInfo(
                    name = testName,
                    result = result,
                    startedAt = 0,
                    finishedAt = 0,
                )
            )
        )
    }


    private suspend fun filterAndCalculateCoverage(
        context: CoverContext,
        filter: FieldFilter,
        expectedProbes: List<ExecClassData>,
        expectedCoverage: Double,
        expectedSessionSize: Int = 1,
    ) {
        val all = storeClient.getAll<StoredSession>()
        assertEquals(expectedSessionSize, all.size)
        val testOverview = all.first().data.testsOverview
        assertEquals(2, testOverview.size)

        val agentId = "ag"
        val buildVersion = "0.1.0"

        val probes = findProbesByFilter(
            storeClient,
            AgentKey(agentId, buildVersion),
            fieldFilter = listOf(filter)
        )

        assertEquals(expectedProbes, probes)

        //calculate coverage by probes
        val asSequence: Sequence<ExecClassData> = probes.asSequence()
        val calcBundleCounters: BundleCounters = asSequence.calcBundleCounters(context, classBytesEmptyBody)
        calcBundleCounters.run {
            println(all)
            println(overlap)
        }
        val calculateCoverageData: CoverageInfoSet = calcBundleCounters.calculateCoverageData(context)
        println(calculateCoverageData)
        //what we need to send all except associatedTests??
        val coverage = calculateCoverageData.coverage
        assertEquals(expectedCoverage, coverage.percentage)
        val buildMethods = calculateCoverageData.buildMethods
        val packageCoverage = calculateCoverageData.packageCoverage
        val tests2 = calculateCoverageData.tests//todo is it need? fill it manual?
        val coverageByTests = calculateCoverageData.coverageByTests
    }
}
