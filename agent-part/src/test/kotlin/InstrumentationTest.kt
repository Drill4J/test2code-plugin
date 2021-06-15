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
//import instrumentation.epam.drill.test2code.jacoco.*
import kotlinx.atomicfu.*
import kotlinx.collections.immutable.*
import org.jacoco.core.analysis.*
import org.jacoco.core.data.*
import org.jacoco.core.internal.analysis.*
import org.junit.jupiter.api.*
import smth.*
import java.io.*
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

    //todo remove test after investigations
    @Test
    fun `instrumented class coverage`() {
        val nameClass = "MethodWithFewIf"
//        val nameClass = "SmthSecond"
//        val className = "C:\\Users\\Maksim_Likhanov\\IdeaProjects\\drill-repositories\\test2code-plugin\\agent-part\\OwnerController.class"
//        val a = SmthSeven::class
        val absClassName =
            "C:\\Users\\Maksim_Likhanov\\IdeaProjects\\drill-repositories\\test2code-plugin\\admin-part\\build\\classes\\java\\test\\$nameClass.class"
//        val name = "org.springframework.samples.petclinic.owner.OwnerController"
        val memoryClassLoader = MemoryClassLoader()
        val bytes = File(absClassName).readBytes()
//        val name = "org/springframework/samples/petclinic/owner/OwnerController"
        memoryClassLoader.addDefinition(nameClass, bytes)
//        memoryClassLoader.addDefinition("OwnerController", bytes)
        val loadClass: Class<*> = memoryClassLoader.loadClass(nameClass)
        val instrumentation = InstrumentationForTest22(loadClass, bytes)
        val instrumentedBytes = instrumentation.instrumentClass()
        File("./$nameClass.class").writeBytes(instrumentedBytes)
        assertTrue { instrumentedBytes.count() > instrumentation.originalBytes.count() }
        val instrumentedClass = instrumentation.instrumentedClass
        InstrumentationForTest22.TestProbeArrayProvider.start(sessionId, false) { dataSeq ->
            _runtimeData.update { it + dataSeq }
        }
        @Suppress("DEPRECATION") val runnable = instrumentedClass.newInstance() as Runnable
        runnable.run()
        val runtimeData = _runtimeData.updateAndGet {
            it + (InstrumentationForTest22.TestProbeArrayProvider.stop(sessionId)
                ?: emptySequence())
        }
        val executionData = ExecutionDataStore()
        runtimeData.forEach {
            executionData.put(ExecutionData(it.id, it.name, it.probes))
            println("probes=${it.probes}")
        }
        val coverageBuilder = CoverageBuilder()
        val analyzer = Analyzer(executionData, coverageBuilder)
        analyzer.analyzeClass(instrumentation.originalBytes, instrumentedClass.name)
        val coverage = coverageBuilder.getBundle("all")
        val counter = coverage.instructionCounter
        val forDebug = ((((coverage as BundleCoverageImpl).packages as java.util.ArrayList<*>)[0] as PackageCoverageImpl).classes as java.util.ArrayList<*>)[0]
        println(counter)
        println(forDebug)
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

