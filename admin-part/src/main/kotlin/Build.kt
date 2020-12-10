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
    val parentVersion: String = "",
    val probes: PersistentMap<Long, ExecClassData> = persistentHashMapOf(),
    val bundleCounters: BundleCounters = BundleCounters.empty,
    val stats: BuildStats = BuildStats(version),
    val tests: BuildTests = BuildTests()
)

@Serializable
internal data class BuildStats(
    @Id val version: String,
    val coverage: Count = zeroCount,
    val methodCount: Count = zeroCount,
    val coverageByType: Map<String, Count> = emptyMap(),
    val scopeCount: Int = 0
)

internal fun BuildCoverage.toCachedBuildStats(
    context: CoverContext
): BuildStats = context.build.stats.copy(
    coverage = count,
    methodCount = methodCount,
    coverageByType = byTestType.map { it.type to it.summary.coverage.count }.toMap(),
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
