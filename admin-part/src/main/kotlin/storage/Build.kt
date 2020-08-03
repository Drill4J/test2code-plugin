package com.epam.drill.plugins.test2code.storage

import com.epam.drill.plugins.test2code.*
import com.epam.drill.plugins.test2code.coverage.*
import com.epam.kodux.*
import kotlinx.serialization.*
import kotlinx.serialization.protobuf.*

@Serializable
internal class StoredClassData(
    @Id val version: String,
    val data: ByteArray
)

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

internal suspend fun StoreClient.loadClassData(
    version: String
): ClassData? = findById<StoredClassData>(version)?.run {
    ProtoBuf.load(ClassData.serializer(), data)
}

internal suspend fun ClassData.store(storage: StoreClient) {
    val stored = ProtoBuf.dump(ClassData.serializer(), this)
    storage.store(StoredClassData(buildVersion, stored))
}

internal suspend fun StoreClient.loadBuilds(
    versions: Set<String>
): List<CachedBuild> = executeInAsyncTransaction { loadBuilds(versions) }

internal fun KoduxTransaction.loadBuilds(
    versions: Set<String>
): List<CachedBuild> = getAll<CachedBuildCoverage>().filter { it.version in versions }.map {
    CachedBuild(version = it.version, coverage = it)
}.map { build ->
    findById<StoredBundles>(build.version)?.run {
        ProtoBuf.load(BundleCounters.serializer(), data)
    }?.let { build.copy(bundleCounters = it) } ?: build
}.map { build ->
    findById<StoredBuildTests>(build.version)?.run {
        ProtoBuf.load(BuildTests.serializer(), data)
    }?.let { build.copy(tests = it) } ?: build
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

