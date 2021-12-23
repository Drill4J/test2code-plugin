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

private val logger = logger {}

/**
 * @param fieldFilter
 * @see TestOverview
 */
suspend fun findProbesByFilter(
    storeClient: StoreClient,
    agentKey: AgentKey,
    fieldFilter: List<FieldFilter>,
): List<ExecClassData> {
    val tests = findTests(storeClient, agentKey, fieldFilter)
    return findProbes(storeClient, tests)
}

private suspend fun findTests(
    storeClient: StoreClient,
    agentKey: AgentKey,
    fieldFilter: List<FieldFilter>
): List<String> {
    val tests = storeClient.findInList<StoredSession, String>(
        whatReturn = "\"typedTest\" -> 'name'",
        listWay = "'data' -> 'testsOverview'",
        listDescription = "items(result text, \"typedTest\" jsonb, details jsonb) ",
        where = "where json_body -> 'agentKey' ->> 'agentId' = '${agentKey.agentId}' " +
                "AND json_body -> 'agentKey' ->> 'buildVersion' = '${agentKey.buildVersion}' ${fieldFilter.toSql()}",
    )
    logger.debug { "for $agentKey with filter '$fieldFilter' found tests: $tests" }
    return tests
}

fun List<FieldFilter>.toSql(): String {
    val string: StringBuilder = StringBuilder()
    this.forEach {
        string.append(it.toSql())
    }
    return string.toString()
}

fun FieldFilter.toSql(): String {
    val wayToObject = field.split(delimiterForWayToObject)
    val newField = when (wayToObject.size) {
        1 -> wayToObject.joinToString(prefix = "AND \"", postfix = "\"")
        2 -> wayToObject.joinToString(prefix = "AND \"", separator = "\" ->> '", postfix = "'")
        else -> throw RuntimeException("not implemented for size ${wayToObject.size}")//todo EPMDJ-8824
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

private suspend fun findProbes(
    storeClient: StoreClient,
    tests: List<String>
) = storeClient.findInList<StoredSession, ExecClassData>(
    whatReturn = "to_jsonb(items)",
    listWay = "'data' -> 'probes'",
    listDescription = "items(\"testName\" text, id text, \"className\" text, probes jsonb) ",
    where = "where \"testName\" in (${tests.toSqlIn()})",
)

fun List<String>.toSqlIn(): String = this.joinToString(prefix = "'", postfix = "'", separator = "', '")
