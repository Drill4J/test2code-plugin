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
package com.epam.drill.plugins.test2code.util

import com.epam.dsm.*
import com.epam.dsm.util.test.*
import org.jetbrains.exposed.sql.transactions.*
import org.junit.jupiter.api.*
import kotlin.test.*

abstract class PostgresBased(private val schema: String) {
    val storeClient = StoreClient(schema)

    @BeforeTest
    fun before() {
        transaction {
            exec("CREATE SCHEMA IF NOT EXISTS $schema")
        }
    }

    @AfterTest
    fun after() {
        TestDatabaseContainer.clearData(listOf(schema))
    }

    companion object {
        @BeforeAll
        @JvmStatic
        fun postgresSetup() {
            TestDatabaseContainer.startOnce()
        }
    }
}