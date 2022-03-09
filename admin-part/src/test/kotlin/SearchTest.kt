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

import com.epam.drill.plugins.test2code.api.*
import com.epam.drill.plugins.test2code.global_filter.*
import kotlin.test.*
import kotlin.test.Test

class SearchTest {

    private val testOverviewResult = "result"
    private val testOverviewDuration = "duration"

    //default values
    @Test
    fun `should get enum default value by field`() {
        val result = readInstanceProperty(TestOverview.empty, testOverviewResult)
        assertEquals("PASSED", result.toString())
    }

    @Test
    fun `should get int default value by field`() {
        val result = readInstanceProperty(TestOverview.empty, testOverviewDuration)
        assertEquals("0", result.toString())
    }

    @Test
    fun `should get string default value by nested field`() {
        val result = readInstanceProperty(TestOverview.empty, listOf("details", "path"))

        assertEquals("", result.toString())
    }

    @Test
    fun `should get string default value by nested field testName`() {
        val result = readInstanceProperty(TestOverview.empty, listOf("details", "testName"))

        assertEquals(DEFAULT_TEST_NAME, result.toString())
    }

}
