package com.epam.drill.plugins.test2code

import com.epam.drill.common.*
import com.epam.drill.plugin.api.*
import com.epam.drill.plugins.test2code.api.*
import com.epam.drill.plugins.test2code.common.api.*
import com.epam.kodux.*
import kotlinx.collections.immutable.*
import kotlinx.serialization.*
import kotlinx.serialization.protobuf.*

internal data class CachedBuild(
    val version: String,
    val probes: PersistentMap<Long, ExecClassData> = persistentHashMapOf(),
    val coverage: CachedBuildCoverage = CachedBuildCoverage(version),
    val tests: BuildTests = BuildTests()
)

@Serializable
internal data class CachedBuildCoverage(
    @Id val version: String,
    val count: Count = zeroCount,
    val arrow: String? = null,
    val risks: Int = 0
)

internal fun BuildCoverage.toCachedBuildCoverage(version: String) = CachedBuildCoverage(
    version = version,
    count = count,
    arrow = arrow?.name,
    risks = risks.total
)

internal suspend fun CachedBuild.store(storage: StoreClient) {
    storage.executeInAsyncTransaction {
        val testData = ProtoBuf.dump(BuildTests.serializer(), tests)
        store(coverage)
        store(StoredBuildTests(version, testData))
    }
}

internal fun CachedBuildCoverage.recommendations(testsToRun: GroupedTests): Set<String> = sequenceOf(
    "Run recommended tests to cover modified methods".takeIf { testsToRun.any() },
    "Update your tests to cover risks".takeIf { risks > 0 }
).filterNotNullTo(mutableSetOf())

internal fun ClassData.toBuildStatsDto(): BuildStatsDto = BuildStatsDto(
    total = methods.count(),
    new = methodChanges.new.count(),
    modified = methodChanges.modified.count(),
    unaffected = methodChanges.unaffected.count(),
    deleted = methodChanges.deleted.count()
)

//TODO move to admin api

fun BuildManager.childrenOf(version: String): List<BuildInfo> {
    return builds.childrenOf(version)
}

fun BuildManager.otherVersions(version: String): List<BuildInfo> {
    return childrenOf("").filter { it.version != version }
}

val Iterable<BuildInfo>.roots: List<BuildInfo>
    get() = filter { it.parentVersion.isBlank() }

fun Iterable<BuildInfo>.childrenOf(version: String): List<BuildInfo> {
    val other = filter { it.version != version }
    val (children, rest) = other.partition { it.parentVersion == version }
    return children + children.flatMap { rest.childrenOf(it.version) }
}
