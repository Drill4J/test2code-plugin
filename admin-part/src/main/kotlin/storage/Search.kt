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
): List<String> {
    val tests = storeClient.findInList<StoredSession, String>(
        whatReturn = "to_jsonb(\"testId\")",
        listWay = "'data' -> 'tests'",
        listDescription = "items(result text, \"testId\" text, details jsonb) ",
        where = "where json_body -> 'agentKey' ->> 'agentId' = '${agentKey.agentId}' " +
                "AND json_body -> 'agentKey' ->> 'buildVersion' = '${agentKey.buildVersion}' ${testOverviewFilter.toSql()}",
    )
    //todo refactor with apply
    logger.debug { "for $agentKey with filter '$testOverviewFilter' found tests: $tests" }
    return tests
}

fun List<TestOverviewFilter>.toSql(): String {
    val string: StringBuilder = StringBuilder()
    this.forEach {
        string.append(it.toSql())
    }
    return string.toString()
}

fun TestOverviewFilter.toSql(): String {
    val wayToObject = field.split(delimiterForWayToObject)
    val newField = when (wayToObject.size) {
        1 -> wayToObject.joinToString(prefix = "AND \"", postfix = "\"")
        2 -> wayToObject.joinToString(prefix = "AND \"", separator = "\" ->> '", postfix = "'")
        else -> throw RuntimeException("not implemented for size ${wayToObject.size}")//todo EPMDJ-8824
    }
    if (wayToObject.size == 1) {
        val fieldDefaultValue = readInstanceProperty(TestOverview.empty, field)
        if (fieldDefaultValue.toString() == value) {
            return "$newField is null"
        }//todo refactoring
    }
    val sql = when (op) {
        FieldOp.EQ -> "$newField = '$value'"
        else -> {
            logger.warn { "does not exist this operation $op" }
            throw RuntimeException("not implemented $op")//todo EPMDJ-8824 new operations
        }
    }
    logger.debug { "sql filter: $sql" }
    return sql
}

@Suppress("UNCHECKED_CAST")
fun readInstanceProperty(instance: Any, propertyName: String): Any? {
    val property = instance::class.members
        // don't cast here to <Any, R>, it would succeed silently
        .first { it.name == propertyName } as KProperty1<Any, *>
    return property.get(instance)
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
