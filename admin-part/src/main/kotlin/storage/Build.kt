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
import com.epam.kodux.*
import kotlinx.serialization.*

private val logger = logger {}

@Serializable
internal class StoredClassData(
    @Id val version: String,
    @StreamSerialization(SerializationType.FST, CompressType.ZSTD)
    val data: ClassData,
) : java.io.Serializable

@Serializable
internal class StoredBundles(
    @Id val version: String,
    @StreamSerialization(SerializationType.FST, CompressType.ZSTD)
    val data: BundleCounters,
) : java.io.Serializable

@Serializable
class StoredBuildTests(
    @Id val version: String,
    @StreamSerialization(SerializationType.FST, CompressType.ZSTD)
    val data: BuildTests,
) : java.io.Serializable

@Serializable
internal class StoredClassBytes(
    @Id val agentId: String,
    @StreamSerialization(SerializationType.FST, CompressType.ZSTD)
    val classBytes: Map<String, ByteArray>,
) : java.io.Serializable

internal suspend fun StoreClient.loadClassData(
    version: String,
): ClassData? = findById<StoredClassData>(version)?.run {
    data
}

internal suspend fun ClassData.store(storage: StoreClient) {
    trackTime("Store class data") {
        storage.store(StoredClassData(buildVersion, this))
    }
}

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
            store(stats)
            trackTime("Store build bundles") {
                store(StoredBundles(version, bundleCounters))
            }
            trackTime("Store build tests") {
                store(StoredBuildTests(version, tests))
            }
        }
    }
}

internal suspend fun Map<String, ByteArray>.storeDb(storage: StoreClient, id: String) {
    logger.debug { "store class bytes ${this.size} for agent = $id" }
    trackTime("storeClassBytes") {
        storage.store(StoredClassBytes(id, this))
    }
}

internal suspend fun StoreClient.loadClassBytes(
    agentId: String,
): Map<String, ByteArray> = trackTime("loadClassBytes") {
    findById<StoredClassBytes>(agentId)?.classBytes ?: let {
        logger.warn { "can not find classBytes for agentId $agentId" }
        emptyMap()
    }
}
