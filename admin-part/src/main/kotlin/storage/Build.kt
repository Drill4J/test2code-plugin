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
import com.epam.kodux.*
import kotlinx.serialization.*

@Serializable
internal class StoredClassData(
    @Id val version: String,
    @StreamSerialization(SerializationType.FST, CompressType.ZSTD)
    val data: ClassData,
) : JvmSerializable

@Serializable
internal class StoredBundles(
    @Id val version: String,
    @StreamSerialization(SerializationType.FST, CompressType.ZSTD)
    val data: BundleCounters,
) : JvmSerializable

@Serializable
class StoredBuildTests(
    @Id val version: String,
    @StreamSerialization(SerializationType.FST, CompressType.ZSTD)
    val data: BuildTests,
) : JvmSerializable

internal suspend fun StoreClient.loadClassData(
    version: String,
): ClassData? = findById<StoredClassData>(version)?.run {
    data
}

internal suspend fun ClassData.store(storage: StoreClient) {
    storage.store(StoredClassData(buildVersion, this))
}

internal suspend fun StoreClient.loadBuild(
    version: String,
): CachedBuild? = findById<BuildStats>(version)?.let { stats ->
    CachedBuild(
        version = version,
        stats = stats,
        bundleCounters = findById<StoredBundles>(version)?.data ?: BundleCounters.empty,
        tests = findById<StoredBuildTests>(version)?.data ?: BuildTests()
    )
}

internal suspend fun CachedBuild.store(storage: StoreClient) {
    storage.executeInAsyncTransaction {
        store(stats)
        store(StoredBundles(version, bundleCounters))
        store(StoredBuildTests(version, tests))
    }
}
