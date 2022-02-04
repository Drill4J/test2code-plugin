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
import com.epam.drill.plugins.test2code.api.BetweenOp
import com.epam.drill.plugins.test2code.api.routes.*
import com.epam.drill.plugins.test2code.api.routes.Routes.Build.Filters.*
import com.epam.dsm.*
import com.epam.dsm.find.*
import kotlinx.serialization.Serializable

/**
 * @see FilterPayload
 * find by agentId; find by name
 */
@Serializable
data class StoredFilter(
    @Id val agentId: String,
    @Id val name: String,
    val attributes: List<TestOverviewFilter>,
    val attributesOp: BetweenOp = BetweenOp.AND,
    val buildVersion: String = "",
)

fun FilterPayload.toStoredFilter(
    agentId: String,
) = StoredFilter(
    agentId = agentId,
    name = name,
    attributes = attributes,
    attributesOp = attributesOp,
    buildVersion = buildVersion
)

@Serializable
data class FilterDto(
    val name: String,
    val id: String,
)

suspend fun Plugin.sendFilterUpdates(filter: StoredFilter? = null, filterId: String) {
    val filtersRoute = Routes.Build().let(Routes.Build::Filters)
    send(
        buildVersion,
        destination = Filter(id = filterId, filters = filtersRoute),
        message = filter ?: ""
    )
    send(
        buildVersion,
        destination = filtersRoute,
        message = storeClient.findBy<StoredFilter> { StoredFilter::agentId eq agentId }.get().map {
            FilterDto(
                name = it.name,
                id = it.hashCode().toString()
            )
        }
    )
}
