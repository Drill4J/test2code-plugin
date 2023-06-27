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

import com.epam.drill.coverage.ClassWithMethods
import com.epam.drill.plugin.api.*
import com.epam.drill.plugins.test2code.api.*
import com.epam.drill.plugins.test2code.api.TestResult.*
import com.epam.drill.plugins.test2code.common.api.ExecClassData
import com.epam.drill.plugins.test2code.common.api.probesOf
import com.epam.drill.plugins.test2code.coverage.*
import com.epam.drill.plugins.test2code.storage.*
import com.epam.drill.plugins.test2code.test.java.*
import com.epam.drill.plugins.test2code.util.*
import kotlinx.coroutines.*
import org.jacoco.core.internal.data.CRC64
import kotlin.test.*


class JavaCoverage2Test : PluginTest() {

    @Test
    fun `jacoco coverage`() = runBlocking {
        val sessionId = genUuid()
        val (coverageData, _) = calculateCoverage(sessionId) {
            this.execSession("test", sessionId, "AUTO") { sessionId ->
                addProbes(sessionId) { listOf(
                    ExecClassData(
                        id = CRC64.classId(ClassWithMethods::class.java.readBytes()),
                        className = ClassWithMethods::class.java.path,
                        testId = "test".hashCode().toString(),
                        probes = probesOf(
                            true, true, true, true, false, false, false)
                    )
                ) }
            }
        }
        coverageData.run {
            println("PERCENTAGE=${coverage.percentage}, COVERED=${coverage.count.covered}")
            assertEquals(2, coverage.methodCount.covered)
            assertTrue(coverage.percentage > 50.0)
        }
    }

    private val classData1 = object : AdminData {
        override suspend fun loadClassBytes(): Map<String, ByteArray> = classMapClass1
        override suspend fun loadClassBytes(buildVersion: String): Map<String, ByteArray> = classMapClass1
    }

    private suspend fun calculateCoverage(
        sessionId: String,
        addProbes: suspend ActiveScope.() -> Unit,
    ): Pair<CoverageInfoSet, CoverContext> {
        val plugin = initPlugin("0.1.0", classData1)
        plugin.initialize()
        val state = plugin.state

        val activeScope = state.activeScope
        activeScope.addProbes()
        state.finishSession(sessionId)
        val finishedScope = activeScope.finish(enabled = true)

        val context = state.coverContext()
        val bundleCounters = finishedScope.calcBundleCounters(context, classMapClass1)
        return bundleCounters.calculateCoverageData(context) to context
    }

    private suspend fun ActiveScope.execSession(
        testName: String,
        sessionId: String,
        testType: String,
        session: ActiveSession? = null,
        result: TestResult = PASSED,
        labels: Set<Label> = emptySet(),
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
                        testName = testName,
                        labels = labels,
                    )
                )
            )
        )
    }
}
