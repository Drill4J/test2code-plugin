/**
 * Copyright 2020 - 2022 EPAM Systems
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

import com.epam.drill.logger.api.NopLogAppender
import com.epam.drill.logger.api.namedLogger
import com.epam.drill.plugin.api.processing.AgentContext
import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.updateAndGet
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.plus
import org.jacoco.core.analysis.Analyzer
import org.jacoco.core.analysis.CoverageBuilder
import org.jacoco.core.analysis.ICounter
import org.jacoco.core.data.ExecutionData
import org.jacoco.core.data.ExecutionDataStore
import java.util.concurrent.ConcurrentHashMap
import kotlin.reflect.KClass
import com.epam.drill.plugin.api.processing.*
import kotlinx.atomicfu.*
import kotlinx.collections.immutable.*
import org.jacoco.core.analysis.*
import org.jacoco.core.data.*
import org.jacoco.core.internal.data.*
import java.io.FileOutputStream
import kotlin.reflect.*


class InstrumentationForTest(kClass: KClass<*>) {

    companion object {
        const val sessionId = "xxx"

        val instrContextStub: AgentContext =
            object : AgentContext {
                override fun get(key: String): String? = when (key) {
                    DRIlL_TEST_NAME_HEADER -> "test"
                    else -> null
                }

                override fun invoke(): String = sessionId

            }
    }

    object TestProbeArrayProvider : SimpleSessionProbeArrayProvider(instrContextStub)

    val instrumenter = DrillInstrumenter(TestProbeArrayProvider)

    val memoryClassLoader = MemoryClassLoader()

    val targetClass = kClass.java

    val originalBytes = targetClass.readBytes()

    val instrumentedClass: Class<*> = loadClass()

    fun runNonInstrumentedClass() {
        runClass(targetClass)
    }

    private fun loadClass(): Class<*> {
        addInstrumentedClass()
        return memoryClassLoader.loadClass(targetClass.name)
    }

    private fun addInstrumentedClass() {
        memoryClassLoader.addDefinition(targetClass.name, instrumentClass())
    }

    fun instrumentClass(name: String = targetClass.name) = instrumenter.instrument(name, originalBytes)!!

    fun runClass(clazz: Class<*> = instrumentedClass) {
        @Suppress("DEPRECATION")
        val runnable = clazz.newInstance() as Runnable
        runnable.run()
    }

    private val _runtimeData = atomic(persistentListOf<ExecDatum>())

    fun runInstrumentedClass() {
        @Suppress("DEPRECATION")
        val runnable = instrumentedClass.newInstance() as Runnable
        runnable.run()
    }

    private fun fillProbes() = TestProbeArrayProvider.run {
        val testKey = TestKey(instrContextStub[DRIlL_TEST_NAME_HEADER] ?: "unspecified", "")
        val execRuntime = runtimes[sessionId]
        if (execRuntime != null) {
            val execDatum = execRuntime.getOrPut(
                Pair(
                    sessionId,
                    testKey
                )
            ) { ConcurrentHashMap<Long, ExecDatum>().apply { fillFromMeta(testKey) } }
            requestThreadLocal.set(execDatum)
        } else {
            requestThreadLocal.remove()
        }
    }

    fun collectCoverage(isInvokedRunnable: Boolean = true): ICounter? {
        TestProbeArrayProvider.start(sessionId, false)
        fillProbes()
        if (isInvokedRunnable) {
            @Suppress("DEPRECATION")
            val runnable = instrumentedClass.newInstance() as Runnable
            runnable.run()
        }
        val runtimeData = _runtimeData.updateAndGet {
            it + (TestProbeArrayProvider.stop(sessionId) ?: emptySequence())
        }
        val executionData = ExecutionDataStore()
        runtimeData.forEach { executionData.put(ExecutionData(it.id, it.name, it.probes.values)) }
        val coverageBuilder = CoverageBuilder()
        val analyzer = Analyzer(executionData, coverageBuilder)
        analyzer.analyzeClass(originalBytes, instrumentedClass.name)
        val coverage = coverageBuilder.getBundle("all")
        return coverage.instructionCounter
    }
}

class MemoryClassLoader : ClassLoader() {
    private val definitions = mutableMapOf<String, ByteArray?>()

    fun addDefinition(name: String, bytes: ByteArray) {
        definitions[name] = bytes
    }

    override fun loadClass(name: String?, resolve: Boolean): Class<*> {
        val bytes = definitions[name]
        return if (bytes != null) {
            defineClass(name, bytes, 0, bytes.size)
        } else {
            super.loadClass(name, resolve)
        }
    }
}

internal fun Class<*>.readBytes(): ByteArray = getResourceAsStream(
    "/${name.replace('.', '/')}.class"
).readBytes()
