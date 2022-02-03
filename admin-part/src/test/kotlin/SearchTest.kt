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
import com.epam.drill.plugins.test2code.storage.*
import org.junit.jupiter.api.*
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


    @Test
    fun `should split list of tests into SQL`() {
        val tests = listOf("test1", "test2")
        assertEquals("'test1', 'test2'", tests.toSqlIn())
        assertEquals("'test1'", listOf("test1").toSqlIn())
    }

    // TestOverviewFilter
    @Test
    fun `should create where query when TestOverviewFilter has one value`() {
        val value = "SKIPPED"
        val testOverviewFilter = TestOverviewFilter(fieldPath = testOverviewResult, values = listOf(FilterValue(value)))
        assertEquals("\"$testOverviewResult\" = '$value'", testOverviewFilter.toSql())
    }

    @Test
    fun `should create where query when TestOverviewFilter has nested field`() {
        val value = "PASSED"
        val field = "details"
        val field2 = "testName"
        assertEquals(
            "\"$field\" ->> '$field2' = '$value'",
            TestOverviewFilter("$field$delimiterForWayToObject$field2", values = listOf(FilterValue(value))).toSql()
        )
    }

    @Test
    fun `should create where query when TestOverviewFilter has few values`() {
        val value = "FAILED"
        val value2 = "SKIPPED"
        val filter = TestOverviewFilter(testOverviewResult, values = listOf(FilterValue(value), FilterValue(value2)))
        assertEquals("(\"$testOverviewResult\" = '$value' OR \"$testOverviewResult\" = '$value2')", filter.toSql())
    }

    //todo
    @Disabled
    @Test
    fun `should create where query when nested three fields`() {
        val value = "PASSED"
        val field = "details"
        val field2 = "params"
        val field3 = "smth"

        assertEquals(
            "\"$field\" -> '$field2' ->> '$field3' = '$value'",
            TestOverviewFilter("$field->$field2->$field3", values = listOf(FilterValue(value))).toSql()
        )
    }

    @Test
    fun `should create sql when list TestOverviewFilter has one element`() {
        val value = "PASSED"
        val field = "details"
        val filter1 = TestOverviewFilter(fieldPath = field, values = listOf(FilterValue(value)))
        assertEquals(" AND \"$field\" = '$value'", listOf(filter1).toSql())
    }

    @Test
    fun `should create sql when list TestOverviewFilter has few elements`() {
        val field = "details"
        val value = "PASSED"

        val value2 = "23"
        val filter1 = TestOverviewFilter(fieldPath = field, values = listOf(FilterValue(value)))
        val filter2 = TestOverviewFilter(fieldPath = testOverviewDuration, values = listOf(FilterValue(value2)))
        assertEquals(" AND \"$field\" = '$value' AND \"$testOverviewDuration\" = '$value2'", listOf(filter1, filter2).toSql())
    }
}
