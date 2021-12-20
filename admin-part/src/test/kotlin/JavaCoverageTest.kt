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
        val pair = calculateCoverage(sessionId) {
            this.execSession("test", sessionId, "MANUAL") { sessionId ->
                addProbes(sessionId) { manualFullProbes }
            }
        }
        val coverageData = pair.first
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
    fun `coverageData for active scope with auto`() {
        runBlocking {
            val sessionId = genUuid()
            val passedTest = "test1"
            val pair = calculateCoverage(sessionId) {
                val session: ActiveSession? = startSession(sessionId = sessionId, testType = AUTO_TEST_TYPE)
                this.execSession(
                    testName = passedTest,
                    sessionId = sessionId,
                    testType = AUTO_TEST_TYPE,
                    session
                ) { sessionId ->
                    addProbes(sessionId) { autoProbes }
                }
                this.execSession(
                    testName = "test2",
                    sessionId = sessionId,
                    testType = AUTO_TEST_TYPE,
                    session,
                    TestResult.FAILED
                ) { sessionId ->
                    addProbes(sessionId) { autoProbes2 }
                }
            }
            val coverageData = pair.first
            coverageData.run {
                println(coverage)
                assertEquals(1, coverage.classCount.total)
                assertTrue(coverage.percentage > 0.0)
                println(packageCoverage)
                println(packageCoverage[0])
                println(associatedTests)
                println(buildMethods)
            }

            val all = storeClient.getAll<StoredSession>()
            assertEquals(1, all.size)
            val testOverview = all.first().data.testsOverview
            assertEquals(2, testOverview.size)

            //todo example: "typedTest" need to convert in "". because Postgresql sensitive of register. maybe do it DSM?
            val tests = storeClient.findInListWhere<StoredSession, String>(
                "\"typedTest\" -> 'name'",
                "'data' -> 'testsOverview') as items(result text, \"typedTest\" jsonb, details jsonb) " +
                        "where result = 'PASSED';"
            )
            assertEquals(listOf(passedTest), tests)
            val probes = storeClient.findInListWhere<StoredSession, ExecClassData>(
                "to_jsonb(items)",
                "'data' -> 'probes') as items(\"testName\" text, id text, \"className\" text, probes jsonb) " +
                        "where \"testName\" in ('${tests[0]}')"//todo add split of tests
            )

            // 3) todo merge probes with the same id
            assertEquals(autoProbes, probes)

            //calculate coverage
            val asSequence: Sequence<ExecClassData> = probes.asSequence()
            val context = pair.second
            val calcBundleCounters: BundleCounters = asSequence.calcBundleCounters(context, classBytesEmptyBody)
            calcBundleCounters.run {
                println(all)
                println(overlap)
            }
            val calculateCoverageData = calcBundleCounters.calculateCoverageData(context)
            println(calculateCoverageData)
            assertEquals(25.0, calculateCoverageData.coverage.percentage)
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
}
