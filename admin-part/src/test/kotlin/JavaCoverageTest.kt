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
import com.epam.drill.plugins.test2code.api.BetweenOp.*
import com.epam.drill.plugins.test2code.api.TestResult.*
import com.epam.drill.plugins.test2code.coverage.*
import com.epam.drill.plugins.test2code.global_filter.*
import com.epam.drill.plugins.test2code.storage.*
import com.epam.drill.plugins.test2code.test.java.*
import com.epam.drill.plugins.test2code.util.*
import kotlinx.coroutines.*
import kotlin.test.*

class JavaCoverageTest : PluginTest() {

    companion object {
        const val AUTO_TEST_TYPE = "AUTO"
    }

    private val passedAutoTest = "test1"
    private val failedAutoTest = "test2"

    @Test
    fun `should collect coverageData when manual session`() = runBlocking {
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
    fun `should filter coverage when auto session has several tests`() {
        runBlocking {
            val (coverageData, context) = runAutoSessionWithTwoTests(passedTest = passedAutoTest)
            coverageData.run {
                assertEquals(1, coverage.classCount.total)
                assertEquals(100.0, coverage.percentage)
            }

            filterAndCalculateCoverage(
                context,
                filter = TestOverviewFilter("result", values = listOf(FilterValue("FAILED"))),
                expectedCoverage = 100.0
            )
        }
    }

    @Test
    fun `should filter coverage when auto session has several tests and filter has default value`() {
        runBlocking {
            val (coverageData, context) = runAutoSessionWithTwoTests(passedTest = passedAutoTest)
            coverageData.run {
                assertEquals(1, coverage.classCount.total)
                assertEquals(100.0, coverage.percentage)
            }

            filterAndCalculateCoverage(
                context,
                filter = TestOverviewFilter("result", values = listOf(FilterValue("PASSED"))),
                expectedCoverage = 25.0
            )
        }
    }

    @Test
    fun `should filter coverage when filter has nested query`() = runBlocking {
        val (coverageData, context) = runAutoSessionWithTwoTests(passedTest = passedAutoTest)
        coverageData.run {
            assertEquals(1, coverage.classCount.total)
            assertEquals(100.0, coverage.percentage)
            assertTrue(coverage.percentage > 0.0)
        }

        filterAndCalculateCoverage(
            context,
            filter = TestOverviewFilter("details->testName", values = listOf(FilterValue(failedAutoTest))),
            expectedCoverage = 100.0
        )
    }

    @Test
    fun `should get 0 coverage when filter does not math any probes`() = runBlocking {
        val (coverageData, context) = runAutoSessionWithTwoTests(passedTest = passedAutoTest)
        coverageData.run {
            assertEquals(1, coverage.classCount.total)
            assertEquals(100.0, coverage.percentage)
        }

        filterAndCalculateCoverage(
            context,
            filter = TestOverviewFilter("details->engine", values = listOf(FilterValue("does not exist"))),
            expectedCoverage = 0.0,
            expectedTestsCount = 0,
            expectedTestTypeCount = 0
        )
    }

    @Test
    fun `should get 0 coverage when filter has 'and' and default`() = runBlocking {
        val (coverageData, context) = runAutoSessionWithTwoTests(passedTest = passedAutoTest)
        coverageData.run {
            assertEquals(1, coverage.classCount.total)
            assertEquals(100.0, coverage.percentage)
        }

        filterAndCalculateCoverage(
            context,
            filter = TestOverviewFilter(
                "result",
                values = listOf(FilterValue("PASSED"), FilterValue("FAILED")),
                valuesOp = AND
            ),
            expectedCoverage = 0.0,
            expectedTestsCount = 0,
            expectedTestTypeCount = 0
        )
    }

    @Test
    fun `should get 0 coverage when filter has 'and'`() = runBlocking {
        val (coverageData, context) = runAutoSessionWithTwoTests(passedTest = passedAutoTest)
        coverageData.run {
            assertEquals(1, coverage.classCount.total)
            assertEquals(100.0, coverage.percentage)
        }

        filterAndCalculateCoverage(
            context,
            filter = TestOverviewFilter(
                "result",
                values = listOf(FilterValue("FAILED"), FilterValue("SKIPPED")),
                valuesOp = AND
            ),
            expectedCoverage = 0.0,
            expectedTestsCount = 0,
            expectedTestTypeCount = 0
        )
    }

    @Test
    fun `should get 100 coverage when auto session has several tests and filter has few values with default`() =
        runBlocking {
            val (coverageData, context) = runAutoSessionWithTwoTests(passedTest = passedAutoTest)
            coverageData.run {
                assertEquals(1, coverage.classCount.total)
                assertEquals(100.0, coverage.percentage)
            }

            filterAndCalculateCoverage(
                context,
                filter = TestOverviewFilter("result", values = listOf(FilterValue("PASSED"), FilterValue("FAILED"))),
                expectedCoverage = 100.0,
                expectedTestsCount = 2,
            )
        }


    @Test
    fun `should get 100 coverage when auto session has several tests and filter has few values OR`() = runBlocking {
        val (coverageData, context) = runAutoSessionWithTwoTests(passedTest = passedAutoTest)
        coverageData.run {
            assertEquals(1, coverage.classCount.total)
            assertEquals(100.0, coverage.percentage)
        }

        filterAndCalculateCoverage(
            context,
            filter = TestOverviewFilter("result", values = listOf(FilterValue("SKIPPED"), FilterValue("FAILED"))),
            expectedCoverage = 100.0,
            expectedTestsCount = 1,
        )
    }

    @Test
    fun `find attributes after collect coverage`() = runBlocking {
        val (coverageData, _) = runAutoSessionWithTwoTests(passedTest = passedAutoTest)
        coverageData.run {
            assertEquals(1, coverage.classCount.total)
            assertEquals(100.0, coverage.percentage)
        }

        val sessionIds = storeClient.sessionIds(agentKey)
        assertEquals(listOf(FAILED.toString(), PASSED.toString()),
            storeClient.attrValues(sessionIds, TestOverview::result.name))
        assertEquals(listOf(passedAutoTest, failedAutoTest),
            storeClient.attrValues(sessionIds, TestDetails::testName.name, isTestDetails = true))
    }

    private suspend fun runAutoSessionWithTwoTests(
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
            testName = failedAutoTest,
            sessionId = sessionId,
            testType = AUTO_TEST_TYPE,
            session = session,
            result = FAILED,
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
        addProbes: suspend ActiveScope.() -> Unit,
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
        result: TestResult = PASSED,
        block: suspend ActiveScope.(String) -> Unit,
    ) {
        val startSessionNew = session ?: startSession(sessionId = sessionId, testType = testType)
        block(sessionId)
        startSessionNew?.addTests(
            listOf(
                TestInfo(
                    id = testName.hashCode().toString(),//like crc32
                    result = result,
                    startedAt = 0,
                    finishedAt = 0,
                    details = TestDetails(
                        testName = testName
                    )
                )
            )
        )
    }


    private suspend fun filterAndCalculateCoverage(
        context: CoverContext,
        filter: TestOverviewFilter,
        expectedCoverage: Double,
        expectedTestsCount: Int = 1,
        expectedTestTypeCount: Int = 1,
        expectedSessionSize: Int = 1,
    ) {
        val all = storeClient.getAll<StoredSession>()
        assertEquals(expectedSessionSize, all.size)
        val testOverview = all.first().data.tests
        assertEquals(2, testOverview.size)

        val agentId = "ag"
        val buildVersion = "0.1.0"

        val agentKey = AgentKey(agentId, buildVersion)

        val filterBundleCounters: BundleCounters = listOf(filter).calcBundleCounters(context,
            classBytesEmptyBody,
            storeClient,
            agentKey)
        assertEquals(expectedTestsCount, filterBundleCounters.byTest.size)
        assertEquals(expectedTestsCount, filterBundleCounters.byTestOverview.size)
        assertEquals(expectedTestTypeCount, filterBundleCounters.byTestType.size)

        val calculateCoverageData: CoverageInfoSet = filterBundleCounters.calculateCoverageData(context)
        assertEquals(expectedCoverage, calculateCoverageData.coverage.percentage)
        //todo implement EPMDJ-8975 overlapping
        assertEquals(0.0, calculateCoverageData.coverage.testTypeOverlap.percentage)
    }
}
