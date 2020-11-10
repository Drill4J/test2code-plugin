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

internal data class Risks(
    val new: List<Method> = emptyList(),
    val modified: List<Method> = emptyList()
)

internal fun BuildCoverage.toCachedBuildCoverage(version: String) = CachedBuildCoverage(
    version = version,
    count = count,
    scopeCount = finishedScopesCount
)

internal fun CoverContext.risksDto(
    counter: BundleCounter = build.bundleCounters.all
): RisksDto = risks.withoutCoverage(counter).run {
    RisksDto(
        newMethods = new.map { it.toCovered() },
        modifiedMethods = modified.map { it.toCovered() }
    )
}

internal fun Risks.filter(predicate: (Method) -> Boolean): Risks = Risks(
    new = new.filter(predicate),
    modified = modified.filter(predicate)
)

internal fun Risks.withoutCoverage(
    bundleCounter: BundleCounter
): Risks = (new + modified).toCoverMap(bundleCounter, true).let { coverMap ->
    filter { it !in coverMap }
}

internal fun Risks.withCoverage(
    bundleCounter: BundleCounter
): Risks = (new + modified).toCoverMap(bundleCounter, true).let { coverMap ->
    filter { it in coverMap }
}

internal fun AgentSummary.recommendations(): Set<String> = sequenceOf(
    "Run recommended tests to cover modified methods".takeIf { testsToRun.any() },
    "Update your tests to cover risks".takeIf { risks.totalCount() > 0 }
).filterNotNullTo(mutableSetOf())

internal fun CoverContext.toBuildStatsDto(): BuildStatsDto = BuildStatsDto(
    total = methods.count(),
    new = methodChanges.new.count(),
    modified = methodChanges.modified.count(),
    unaffected = methodChanges.unaffected.count(),
    deleted = methodChanges.deleted.count()
)
