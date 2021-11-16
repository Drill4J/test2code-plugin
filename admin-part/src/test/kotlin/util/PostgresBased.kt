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
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import ru.yandex.qatools.embed.postgresql.EmbeddedPostgres
import ru.yandex.qatools.embed.postgresql.distribution.Version

abstract class PostgresBased(private val schema: String) {
    val storeClient = StoreClient(schema)

    @kotlin.test.AfterTest
    fun after() {
        transaction {
            exec("DROP SCHEMA $schema CASCADE")
        }
    }

    @kotlin.test.BeforeTest
    fun before() {
        transaction {
            exec("CREATE SCHEMA IF NOT EXISTS $schema")
        }
    }

    companion object {
        lateinit var postgres: EmbeddedPostgres

        @BeforeAll
        @JvmStatic
        fun postgresSetup() {
            postgres = EmbeddedPostgres(Version.V10_6)
            val host = "localhost"
            val port = 5432
            val dbName = "dbName"
            val userName = "userName"
            val password = "password"
            postgres.start(
                host,
                port,
                dbName,
                userName,
                password
            )
            Database.connect(
                "jdbc:postgresql://$host:$port/$dbName", driver = "org.postgresql.Driver",
                user = userName, password = password
            ).also {
                println { "Connected to db ${it.url}" }
            }
        }

        @AfterAll
        @JvmStatic
        fun postgresClean() {
            postgres.close()
        }

    }
}
