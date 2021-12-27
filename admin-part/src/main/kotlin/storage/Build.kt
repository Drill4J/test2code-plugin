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
import com.epam.drill.plugins.test2code.coverage.*
import com.epam.drill.plugins.test2code.util.*
import com.epam.dsm.*
import kotlinx.serialization.*

@Serializable
@StreamSerialization
internal class StoredClassData(
    @Id val agentKey: AgentKey,
    val data: ClassData,
)

@Serializable
@StreamSerialization
internal class StoredBundles(
    @Id val agentKey: AgentKey,
    val data: BundleCounters,
)

@Serializable
@StreamSerialization
class StoredBuildTests(
    @Id val agentKey: AgentKey,
    val data: BuildTests,
)

internal suspend fun StoreClient.loadClassData(
    agentKey: AgentKey,
): ClassData? = findById<StoredClassData>(agentKey)?.run {
    val lambdaHash = findById(agentKey) ?: LambdaHash(agentKey)
    data.copy(methods = data.methods.map {
        it.copy(
            lambdasHash = lambdaHash.hash[it.key] ?: emptyMap(),
        )
    })
}

internal suspend fun ClassData.store(storage: StoreClient) {
    trackTime("Store class data") {
        val hash = methods.mapNotNull { method ->
            method.takeIf { it.lambdasHash.any() }?.let { it.key to it.lambdasHash }
        }.toMap()
        storage.store(StoredClassData(agentKey, this))
        storage.store(LambdaHash(agentKey, hash))
    }
}

internal suspend fun StoreClient.removeClassData(agentKey: AgentKey) = deleteById<StoredClassData>(agentKey)

internal suspend fun StoreClient.loadBuild(
    agentKey: AgentKey,
): CachedBuild? = findById<BuildStats>(agentKey)?.let { stats ->
    trackTime("Load build") {
        CachedBuild(
            agentKey = agentKey,
            stats = stats,
            bundleCounters = findById<StoredBundles>(agentKey)?.data ?: BundleCounters.empty,
            tests = findById<StoredBuildTests>(agentKey)?.data ?: BuildTests()
        )
    }
}

internal suspend fun CachedBuild.store(storage: StoreClient) {
    storage.executeInAsyncTransaction {
        trackTime("Store build total") {
            val schema = storage.schema
            store(stats, schema)
            trackTime("Store build bundles") {
                store(StoredBundles(agentKey, bundleCounters), schema)
            }
            trackTime("Store build tests") {
                store(StoredBuildTests(agentKey, tests), schema)
            }
        }
    }
}

internal suspend fun StoreClient.removeBuild(
    agentKey: AgentKey,
) = executeInAsyncTransaction {
    deleteById<BuildStats>(agentKey)
    deleteById<StoredBundles>(agentKey)
    deleteById<StoredBuildTests>(agentKey)
}


internal suspend fun StoreClient.removeBuildData(
    agentKey: AgentKey,
    scopeManager: ScopeManager,
) = executeInAsyncTransaction {
    logger.debug { "starting to remove build '$agentKey' data..." }
    removeClassData(agentKey)
    removeBuild(agentKey)
    scopeManager.deleteByVersion(agentKey)
}

