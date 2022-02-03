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
package com.epam.drill.plugins.test2code.storage

import com.epam.drill.plugins.test2code.*
import com.epam.drill.plugins.test2code.api.*
import com.epam.drill.plugins.test2code.common.api.*
import com.epam.dsm.*
import kotlin.reflect.*

private val logger = logger {}

/**
 * @param testOverviewFilter
 * @see TestOverview
 */
suspend fun findProbesByFilter(
    storeClient: StoreClient,
    agentKey: AgentKey,
    testOverviewFilter: List<TestOverviewFilter>,
): List<ExecClassData> {
    val tests = findTests(storeClient, agentKey, testOverviewFilter)
    return findAutoProbes(storeClient, tests)
}

private suspend fun findTests(
    storeClient: StoreClient,
    agentKey: AgentKey,
    testOverviewFilter: List<TestOverviewFilter>,
): List<String> = storeClient.findInList<StoredSession, String>(
    whatReturn = "to_jsonb(\"testId\")",
    listWay = "'data' -> 'tests'",
    listDescription = "items(result text, \"testId\" text, details jsonb) ",
    where = "where json_body -> 'agentKey' ->> 'agentId' = '${agentKey.agentId}' " +
            "AND json_body -> 'agentKey' ->> 'buildVersion' = '${agentKey.buildVersion}' ${testOverviewFilter.toSql()}",
).apply {
    logger.debug { "for $agentKey with filter '$testOverviewFilter' found tests: $this" }
}


fun List<TestOverviewFilter>.toSql(): String = run {
    val filterSql = StringBuilder()
    forEach {
        filterSql.append(" AND ${it.toSql()}")
    }
    logger.debug { "sql for filters: $filterSql" }
    filterSql.toString()
}

fun TestOverviewFilter.toSql(): String {
    val wayToObject: List<String> = fieldPath.split(delimiterForWayToObject)
    val field = toSqlPathField(wayToObject)

    val defaultField = readInstanceProperty(TestOverview.empty, wayToObject).toString()
    val sql = StringBuilder("")
    values.forEachIndexed { index, it ->
        if (index > 0) {
            sql.append(" $valuesOp ")
        }
        if (defaultField == it.value) {
            sql.append("$field is null")
        } else {
            when (it.op) {
                FieldOp.EQ -> sql.append("$field = '${it.value}'")
                else -> {
                    logger.warn { "does not exist this operation ${it.op}" }
                    throw RuntimeException("not implemented ${it.op}")//todo EPMDJ-8975 new operations
                }
            }
        }
    }
    val finalSql = if (values.size > 1) "($sql)" else sql
    logger.debug { "sql filter by one filter: $finalSql" }
    return finalSql.toString()
}

private fun toSqlPathField(wayToObject: List<String>): String {
    val field = when (wayToObject.size) {
        1 -> wayToObject.joinToString(prefix = "\"", postfix = "\"")
        2 -> wayToObject.joinToString(prefix = "\"", separator = "\" ->> '", postfix = "'")
        else -> throw RuntimeException("not implemented for size ${wayToObject.size}")//todo EPMDJ-8975
    }
    return field
}

@Suppress("UNCHECKED_CAST")
fun readInstanceProperty(instance: Any, propertyName: String): Any {
    val property = instance::class.members
        // don't cast here to <Any, R>, it would succeed silently
        .first { it.name == propertyName } as KProperty1<Any, *>
    return property.get(instance) ?: ""
}

@Suppress("UNCHECKED_CAST")
fun readInstanceProperty(instance: Any, properties: List<String>): Any {
    //todo or recursive?
    var temp: Any = instance
    properties.forEach {
        temp = readInstanceProperty(temp, it)
    }
    return temp
}

private suspend fun findAutoProbes(
    storeClient: StoreClient,
    tests: List<String>,
) = storeClient.findInList<StoredSession, ExecClassData>(
    whatReturn = "to_jsonb(items)",
    listWay = "'data' -> 'probes'",
    listDescription = "items(\"testName\" text, \"testId\" text, id text, \"className\" text, probes jsonb) ",
    where = "where \"testId\" in (${tests.toSqlIn()})",
)

fun List<String>.toSqlIn(): String = this.joinToString(prefix = "'", postfix = "'", separator = "', '")
