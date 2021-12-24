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

import com.epam.drill.jacoco.*
import org.junit.jupiter.api.*
import kotlin.random.*
import kotlin.test.*
import kotlin.test.Test


class ExecRuntimeTest {
    private val threadLocal = ThreadLocal<Array<ExecDatum?>>()
    private val className = "foo/bar/Foo"
    private val testKey = TestKey("test","id")

    @AfterTest
    fun removeThreadLocal() {
        threadLocal.remove()
    }

    @Test
    fun `put - should put by existing index`() {
        val runtime = ExecRuntime {}
        val probesByClasses = runtime.getOrPut(testKey) { arrayOfNulls(9) }
        runtime.put(7) { ExecDatum(1L, className, AgentProbes()) }
        assertNotNull(probesByClasses[7])
    }

    @Test
    fun `put - not existing index`() {
        val runtime = ExecRuntime {}
        val probesByClasses = runtime.getOrPut(testKey) { arrayOfNulls(5) }
        assertDoesNotThrow {
            runtime.put(7) { ExecDatum(1L, className, AgentProbes()) }
        }
        assertEquals(5, probesByClasses.size)
    }

    @Test
    fun `collect - empty probes`() {
        val runtime = ExecRuntime {}
        val probesByClasses = runtime.getOrPut(testKey) { arrayOfNulls(9) }
        runtime.put(7) { ExecDatum(1L, className, AgentProbes()) }
        assertNotNull(probesByClasses[7])
        assertTrue { runtime.collect().none() }
    }

    @Test
    fun `collect - should add probes`() {
        val runtime = ExecRuntime {}
        runtime.fillProbe()
        threadLocal.get()[0]?.probes?.set(Random.nextInt(5))
        val data = runtime.collect()
        assertTrue { data.any() }
        assertTrue { threadLocal.get()[0] != null }
        val probesByClass = data.byClass(className)
        assertEquals(1, probesByClass.probes.values.count { it })
        assertEquals(4, probesByClass.probes.values.count { !it })
        assertEquals(testKey.testName, probesByClass.testName)
        assertEquals(className, probesByClass.name)
    }


    @Test
    fun `collect - should add probes after collect`() {
        val runtime = ExecRuntime {}
        runtime.fillProbe()
        threadLocal.get()[0]?.probes?.set(1)
        val data = runtime.collect()
        assertTrue { data.any() }
        assertTrue { threadLocal.get()[0] != null }
        threadLocal.get()[0]?.probes?.set(2)
        //if execData is cleared, this assert fails
        assertTrue { runtime.collect().any() }
    }

    @Test
    fun `collect - should not collect the same probes`() {
        val runtime = ExecRuntime {}
        runtime.fillProbe()
        threadLocal.get()[0]?.probes?.set(1)
        threadLocal.get()[0]?.probes?.set(2)
        val firstData = runtime.collect()
        assertTrue { firstData.any() }

        runtime.fillProbe()
        threadLocal.get()[0]?.probes?.set(3)
        val secondData = runtime.collect()
        assertTrue { secondData.any() }
        assertEquals(2, firstData.byClass(className).probes.values.count { it })
        assertEquals(1, secondData.byClass(className).probes.values.count { it })
    }

    private fun ExecRuntime.fillProbe() {
        val value = getOrPut(testKey) { arrayOfNulls(5) }
        threadLocal.set(value)
        put(0) { ExecDatum(1L, className, AgentProbes(5), it.testName!!) }
    }
}

fun Sequence<ExecDatum>.byClass(name: String): ExecDatum = first { it.name == name }
