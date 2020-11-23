package com.epam.drill.plugins.test2code

import com.epam.drill.plugins.test2code.api.*
import com.epam.drill.plugins.test2code.common.api.*
import com.epam.drill.plugins.test2code.coverage.*
import com.epam.drill.plugins.test2code.group.*
import com.epam.kodux.*
import kotlinx.collections.immutable.*
import kotlinx.serialization.*

internal data class CachedBuild(
    val version: String,
    val probes: PersistentMap<Long, ExecClassData> = persistentHashMapOf(),
    val bundleCounters: BundleCounters = BundleCounters.empty,
    val coverage: CachedBuildCoverage = CachedBuildCoverage(version),
    val tests: BuildTests = BuildTests()
)

@Serializable
internal data class CachedBuildCoverage(
    @Id val version: String,
    val count: Count = zeroCount,
    val scopeCount: Int = 0
)

internal fun BuildCoverage.toCachedBuildCoverage(version: String) = CachedBuildCoverage(
    version = version,
    count = count,
    scopeCount = finishedScopesCount
)

internal fun AgentSummary.recommendations(): Set<String> = sequenceOf(
    "Run recommended tests to cover modified methods".takeIf { testsToRun.any() },
    "Update your tests to cover risks".takeIf { riskCounts.total > 0 }
).filterNotNullTo(mutableSetOf())

internal fun CoverContext.toBuildStatsDto(): BuildStatsDto = BuildStatsDto(
    parentVersion = parentBuild?.version ?: "",
    total = methods.count(),
    new = methodChanges.new.count(),
    modified = methodChanges.modified.count(),
    unaffected = methodChanges.unaffected.count(),
    deleted = methodChanges.deleted.count()
)
