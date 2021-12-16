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
import com.epam.drill.plugins.test2code.common.api.*
import com.epam.drill.plugins.test2code.coverage.*
import com.epam.drill.plugins.test2code.util.*
import com.epam.dsm.*
import kotlinx.serialization.*

@Serializable
@StreamSerialization
internal class StoredClassData(
    @Id val version: String,
    val data: ClassData,
)

@Serializable
@StreamSerialization
internal class StoredBundles(
    @Id val version: String,
    val data: BundleCounters,
)

@Serializable
@StreamSerialization
class StoredBuildTests(
    @Id val version: String,
    val data: BuildTests,
)

internal suspend fun StoreClient.loadClassData(
    version: String,
): ClassData? = findById<StoredClassData>(version)?.run {
    val lambdaHash = findById(version) ?: LambdaHash(version)
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
        storage.store(StoredClassData(buildVersion, this))
        storage.store(LambdaHash(buildVersion, hash))
    }
}

internal suspend fun StoreClient.removeClassData(version: String) = deleteById<StoredClassData>(version)

internal suspend fun StoreClient.loadBuild(
    version: String,
): CachedBuild? = findById<BuildStats>(version)?.let { stats ->
    trackTime("Load build") {
        CachedBuild(
            version = version,
            stats = stats,
            bundleCounters = findById<StoredBundles>(version)?.data ?: BundleCounters.empty,
            tests = findById<StoredBuildTests>(version)?.data ?: BuildTests()
        )
    }
}

internal suspend fun CachedBuild.store(storage: StoreClient) {
    storage.executeInAsyncTransaction {
        trackTime("Store build total") {
            val schema = storage.schema
            store(stats, schema)
            trackTime("Store build bundles") {
                store(StoredBundles(version, bundleCounters), schema)
            }
            trackTime("Store build tests") {
                store(StoredBuildTests(version, tests), schema)
            }
        }
    }
}

internal suspend fun StoreClient.removeBuild(
    version: String,
) = executeInAsyncTransaction {
    deleteById<BuildStats>(version)
    deleteById<StoredBundles>(version)
    deleteById<StoredBuildTests>(version)
}


internal suspend fun StoreClient.removeBuildData(
    buildVersion: String,
    scopeManager: ScopeManager,
) = executeInAsyncTransaction {
    logger.debug { "starting to remove build '$buildVersion' data..." }
    removeClassData(buildVersion)
    removeBuild(buildVersion)
    scopeManager.deleteByVersion(buildVersion)
}

