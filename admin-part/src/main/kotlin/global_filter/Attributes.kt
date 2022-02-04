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
package com.epam.drill.plugins.test2code.global_filter

import com.epam.drill.plugins.test2code.*
import com.epam.drill.plugins.test2code.api.*
import com.epam.drill.plugins.test2code.api.routes.*
import com.epam.drill.plugins.test2code.api.routes.Routes.Build.Attributes.*
import com.epam.drill.plugins.test2code.storage.*
import com.epam.dsm.*
import com.epam.dsm.find.*
import com.epam.dsm.util.*
import kotlinx.coroutines.*
import kotlin.reflect.*
import kotlin.reflect.full.*

val staticAttributes = staticAttr()

suspend fun Plugin.sendAttributes() {
    val attributesRoute = Routes.Build().let(Routes.Build::Attributes)
    val dynamicAttr = setOf<String>()//todo take dynamic attr from metadata/params
    val attributes = staticAttributes.union(dynamicAttr)
    send(
        buildVersion,
        destination = attributesRoute,
        message = attributes
    )
    val sessionIds: List<String> = storeClient.sessionIds(agentKey)
    //todo refactor : union testOverview & testDetails
    attributes
        .filterNot { it.contains(PATH_DELIMITER) }
        .map { attribute ->
            val values = attrValues<TestOverview>(sessionIds, attribute)
            send(
                buildVersion,
                destination = AttributeValues(attributesRoute, attribute),
                message = values.toSet()
            )
        }
    //todo need to check
    attributes
        .filter { it.contains(PATH_DELIMITER) }.map {
            it.substringAfter(PATH_DELIMITER)
        }.map { attribute ->
            val values = attrValues<TestDetails>(sessionIds, attribute)
            send(
                buildVersion,
                destination = AttributeValues(attributesRoute, attribute),
                message = values.toSet()
            )

        }
}

private suspend inline fun <reified T : Any> Plugin.attrValues(
    sessionIds: List<String>,
    attribute: String,
): List<String> = storeClient.findBy<T> { containsParentId(sessionIds) }
    .distinct()
    .getStrings(attribute)

//@Suppress("UNCHECKED_CAST")
//private fun example(): Map<String, KClass<*>> {
//    val kClass: KClass<Int> = Int::class
//    val mapOf = mapOf<String, KClass<*>>("duration" to kClass, "testId" to String::class, "result" to String::class)
////    return mapOf as Map<String, KClass<Any>>
//    return mapOf
//}
//
//private suspend inline fun <reified T : Any> KClass<T>.findValues(
//    storeClient: StoreClient,
//    sessionIds: List<String>,
//    attribute: String,
//): List<T> {
//    val values = storeClient.findBy<TestOverview> { containsParentId(sessionIds) }
////                .distinct()//todo it is null when default values so it can not deserialize
//        //todo maybe store all values to easy search
//        .getAndMap<TestOverview, T>(attribute).distinct()
//    return values
//}

fun staticAttr(): Set<String> {
    val details = TestOverview::details.name
    val testOverviewAttributes = TestOverview::class.fields(listOf(details))
    val testDetailsAttr = TestDetails::class.fields(
        listOf(TestDetails::metadata.name, TestDetails::params.name),
        "$details$PATH_DELIMITER")
    return testOverviewAttributes.union(testDetailsAttr)
}

private fun KClass<*>.fields(
    exp: List<String>,
    prefix: String = "",
): List<String> {
    val set2 = this.memberProperties.filterNot { exp.contains(it.name) }.map {
        "$prefix${it.name}"
    }
    return set2
}

