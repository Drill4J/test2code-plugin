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

    @Test
    fun `should split list of tests into SQL`() {
        val tests = listOf("test1", "test2")
        assertEquals("'test1', 'test2'", tests.toSqlIn())
        assertEquals("'test1'", listOf("test1").toSqlIn())
    }

    @Test
    fun `should create where query when use FieldFilter`() {
        val value = "PASSED"
        val field = "details"
        assertEquals("AND \"$field\" = '$value'", FieldFilter(field, value = value).toSql())
    }

    @Test
    fun `should create where query when use FieldFilter few fields`() {
        val value = "PASSED"
        val field = "details"
        val field2 = "testName"
        assertEquals(
            "AND \"$field\" ->> '$field2' = '$value'",
            FieldFilter("$field->$field2", value = value).toSql()
        )
    }

    //todo
    @Disabled
    @Test
    fun `should create where query when use FieldFilter three fields`() {
        val value = "PASSED"
        val field = "details"
        val field2 = "params"
        val field3 = "smth"

        assertEquals(
            "AND \"$field\" -> '$field2' ->> '$field3' = '$value'",
            FieldFilter("$field->$field2->$field3", value = value).toSql()
        )
    }
}
