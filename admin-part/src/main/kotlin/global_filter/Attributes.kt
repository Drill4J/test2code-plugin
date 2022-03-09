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
import kotlin.reflect.*
import kotlin.reflect.full.*

val staticAttributes = staticAttr()

suspend fun Plugin.sendAttributes() {
    val attributesRoute = Routes.Build().let(Routes.Build::Attributes)
    val dynamicAttr = setOf<String>()//todo take dynamic attr from metadata/params
    val attributes = staticAttributes.union(dynamicAttr)
    logger.debug { "start send attributes: $attributes" }
    send(
        buildVersion,
        destination = attributesRoute,
        message = attributes
    )
    val sessionIds = storeClient.sessionIds(agentKey)
    attributes.map { attribute ->
        val values = if (attribute.contains(PATH_DELIMITER)) {
            storeClient.attrValues(sessionIds, attribute.substringAfter(PATH_DELIMITER), isTestDetails = true)
        } else storeClient.attrValues(sessionIds, attribute)
        logger.debug { "send attr '$attribute' values '$values'" }//todo change to trace after testing
        send(
            buildVersion,
            destination = AttributeValues(attributesRoute, attribute),
            message = values.toSet()
        )
    }
}

suspend inline fun StoreClient.attrValues(
    sessionIds: List<String>,
    attribute: String,
    isTestDetails: Boolean = false,
): List<String> = findBy<TestOverview> { containsParentId(sessionIds) }
    .distinct()
    .getStrings(FieldPath(TestOverview::details.name, attribute).takeIf { isTestDetails }
        ?: FieldPath(attribute))

fun staticAttr(): Set<String> {
    val details = TestOverview::details.name
    val testOverviewAttributes = TestOverview::class.nameFields(exceptFields = listOf(details))
    val testDetailsAttr = TestDetails::class.nameFields(
        exceptFields = listOf(TestDetails::metadata.name, TestDetails::params.name),
        prefix = "$details$PATH_DELIMITER")
    return testOverviewAttributes.union(testDetailsAttr)
}

private fun KClass<*>.nameFields(
    exceptFields: List<String>,
    prefix: String = "",
): List<String> {
    return this.memberProperties.filterNot { exceptFields.contains(it.name) }.map {
        "$prefix${it.name}"
    }
}

