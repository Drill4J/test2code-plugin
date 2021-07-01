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
package com.epam.drill.instrumentation

import com.epam.drill.instrumentation.data.*
import com.epam.drill.plugins.test2code.*
import org.jacoco.core.analysis.*
import kotlin.reflect.*
import kotlin.test.*

class InstrumentationTest {

    @Test
    fun `instrumented class should be larger the the original`() {
        val instrumentation = InstrumentationForTest(InvokeBigConditions::class)
        val instrumentedBytes = instrumentation.instrumentClass()
        assertTrue { instrumentedBytes.count() > instrumentation.originalBytes.count() }
    }

    @Test
    fun `should instrumented without coverage`() {
        val counter = InstrumentationForTest(InvokeBigConditions::class).collectCoverage(false)
        assertEquals(0, counter?.coveredCount)
    }

    @Test
    fun `should instrumented with coverage`() {
        val counter = InstrumentationForTest(InvokeBigConditions::class).collectCoverage()
        assertTrue { counter?.coveredCount!! > 2_000 }
        assertTrue { counter?.missedCount!! < 500 }
    }

    @Test
    fun `should instrumented with coverage for cycles`() {
        InstrumentationForTest(InvokeCycles::class).collectCoverage()
    }

}
