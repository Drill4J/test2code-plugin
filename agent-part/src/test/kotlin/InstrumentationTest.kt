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

import com.epam.drill.plugins.test2code.InstrumentationForTest.Companion.sessionId
import kotlinx.atomicfu.*
import kotlinx.collections.immutable.*
import org.jacoco.core.analysis.*
import org.jacoco.core.data.*
import org.junit.jupiter.api.*
import kotlin.test.*
import kotlin.test.Test

class InstrumentationTest {

    private val _runtimeData = atomic(persistentListOf<ExecDatum>())

    @Test
    fun `instrumented class should be larger the the original`() {
        val instrumentation = InstrumentationForTest(TestTarget::class)
        val instrumentedBytes = instrumentation.instrumentClass()
        assertTrue { instrumentedBytes.count() > instrumentation.originalBytes.count() }
    }

    @Test
    fun `should provide coverage for run with the instrumented class`() {
        val instrumentation = InstrumentationForTest(TestTarget::class)
        val instrumentedClass = instrumentation.instrumentedClass
        InstrumentationForTest.TestProbeArrayProvider.start(sessionId, false) { dataSeq ->
            _runtimeData.update { it + dataSeq }
        }
        @Suppress("DEPRECATION") val runnable = instrumentedClass.newInstance() as Runnable
        runnable.run()
        val runtimeData = _runtimeData.updateAndGet {
            it + (InstrumentationForTest.TestProbeArrayProvider.stop(sessionId)
                ?: emptySequence())
        }
        val executionData = ExecutionDataStore()
        runtimeData.forEach { executionData.put(ExecutionData(it.id, it.name, it.probes)) }
        val coverageBuilder = CoverageBuilder()
        val analyzer = Analyzer(executionData, coverageBuilder)
        analyzer.analyzeClass(instrumentation.originalBytes, instrumentedClass.name)
        val coverage = coverageBuilder.getBundle("all")
        val counter = coverage.instructionCounter
        assertEquals(27, counter.coveredCount)
        assertEquals(2, counter.missedCount)
    }

    @Test
    fun `should associate execution data with test name and type gathered from request headers`() {
        val instrumentation = InstrumentationForTest(TestTarget::class)
        val instrumentedClass = instrumentation.instrumentedClass
        InstrumentationForTest.TestProbeArrayProvider.start(sessionId, false)
        @Suppress("DEPRECATION") val runnable = instrumentedClass.newInstance() as Runnable
        runnable.run()
        val runtimeData = InstrumentationForTest.TestProbeArrayProvider.stop(sessionId)!!.toList()
        val assertions = runtimeData.map { execDatum ->
            { assertEquals("test", execDatum.testName) }
        }.toTypedArray()
        assertAll(*assertions)
    }
}

