package com.epam.drill.plugins.test2code.storage

import com.epam.drill.plugins.test2code.*
import com.epam.drill.plugins.test2code.coverage.*
import com.epam.kodux.*
import kotlinx.serialization.*
import kotlinx.serialization.protobuf.*

@Serializable
internal class StoredBundles(
    @Id val version: String,
    val data: ByteArray
)

@Serializable
class StoredBuildTests(
    @Id val version: String,
    val data: ByteArray
)

internal fun KoduxTransaction.loadBuilds(builds: AtomicCache<String, CachedBuild>) {
    getAll<CachedBuildCoverage>().forEach { stored ->
        builds(stored.version) { CachedBuild(version = stored.version, coverage = stored) }
    }
    getAll<StoredBundles>().forEach { stored ->
        builds(stored.version) {
            it?.copy(bundleCounters = ProtoBuf.load(BundleCounters.serializer(), stored.data))
        }
    }
    getAll<StoredBuildTests>().forEach { stored ->
        builds(stored.version) {
            it?.copy(tests = ProtoBuf.load(BuildTests.serializer(), stored.data))
        }
    }
}

internal suspend fun CachedBuild.store(storage: StoreClient) {
    storage.executeInAsyncTransaction {
        val bundleData = ProtoBuf.dump(BundleCounters.serializer(), bundleCounters)
        val testData = ProtoBuf.dump(BuildTests.serializer(), tests)
        store(coverage)
        store(StoredBundles(version, bundleData))
        store(StoredBuildTests(version, testData))
    }
}

