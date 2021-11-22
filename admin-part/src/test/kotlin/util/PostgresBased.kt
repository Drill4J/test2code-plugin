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
import com.zaxxer.hikari.*
import org.jetbrains.exposed.sql.transactions.*
import org.junit.jupiter.api.*
import org.testcontainers.containers.*
import org.testcontainers.containers.wait.strategy.*

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
        @BeforeAll
        @JvmStatic
        fun postgresSetup() {
            val port = 5432
            val dbName = "dbName"
            val postgresContainer = PostgreSQLContainer<Nothing>("postgres:12").apply {
                withDatabaseName(dbName)
                withExposedPorts(port)
                waitingFor(Wait.forLogMessage(".*database system is ready to accept connections.*\\s", 2))
                start()
            }
            println("started container with id ${postgresContainer.containerId}.")
            DatabaseFactory.init(HikariDataSource(HikariConfig().apply {
                this.driverClassName = "org.postgresql.Driver"
                this.jdbcUrl =
                    "jdbc:postgresql://${postgresContainer.host}:${postgresContainer.getMappedPort(port)}/$dbName"
                this.username = postgresContainer.username
                this.password = postgresContainer.password
                this.maximumPoolSize = 3
                this.isAutoCommit = false
                this.transactionIsolation = "TRANSACTION_REPEATABLE_READ"
                this.validate()
            }))
        }
    }
}
