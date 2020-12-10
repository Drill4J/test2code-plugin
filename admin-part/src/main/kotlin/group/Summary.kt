package com.epam.drill.plugins.test2code.group

import com.epam.drill.plugins.test2code.*
import com.epam.drill.plugins.test2code.api.*
import com.epam.drill.plugins.test2code.coverage.*
import kotlinx.atomicfu.*
import kotlinx.collections.immutable.*

//TODO move agent summary out of the group package

internal data class AgentSummary(
    val name: String,
    val buildVersion: String,
    val coverage: Count,
    val coverageByType: Map<String, Count>,
    val methodCount: Count,
    val scopeCount: Int,
    val arrow: ArrowType,
    val riskCounts: RiskCounts = RiskCounts(),
    val risks: TypedRisks,
    val tests: GroupedTests,
    val testDuration: Long,
    val testsToRun: GroupedTests,
    val durationByType: GroupedDuration
)
internal typealias GroupedDuration = Map<String, Long>

internal typealias AgentSummaries = PersistentMap<String, AgentSummary>

internal val summaryAggregator = SummaryAggregator()

internal class SummaryAggregator : (String, String, AgentSummary) -> AgentSummary {
    private val _summaryCache = atomic(
        persistentHashMapOf<String, AgentSummaries>()
    )

    override operator fun invoke(
        serviceGroup: String,
        agentId: String,
        agentSummary: AgentSummary
    ): AgentSummary = run {
        val cache = _summaryCache.updateAndGet {
            val curAgentSummary = it[serviceGroup] ?: persistentHashMapOf()
            it.put(serviceGroup, curAgentSummary.put(agentId, agentSummary))
        }
        cache[serviceGroup]?.values?.reduce { acc, element ->
            acc + element
        } ?: agentSummary
    }

    fun getSummaries(serviceGroup: String): Map<String, AgentSummary> = _summaryCache.value[serviceGroup] ?: emptyMap()
}

internal fun CachedBuild.toSummary(
    agentName: String,
    testsToRun: GroupedTests,
    risks: TypedRisks,
    coverageByTests: CoverageByTests? = null,
    parentCoverageCount: Count? = null
): AgentSummary = AgentSummary(
    name = agentName,
    buildVersion = version,
    coverage = stats.coverage,
    methodCount = stats.methodCount,
    coverageByType = stats.coverageByType,
    scopeCount = stats.scopeCount,
    arrow = parentCoverageCount.arrowType(stats.coverage),
    risks = risks,
    testDuration = coverageByTests?.all?.duration ?: 0L,
    durationByType = coverageByTests?.byType?.groupBy { it.type }
        ?.mapValues { (_, values) -> values.map { it.summary.duration }.sum() }
        ?: emptyMap(),
    tests = tests.tests,
    testsToRun = testsToRun.withoutCoverage(bundleCounters)
).run { copy(riskCounts = risks.toCounts()) }

internal fun AgentSummary.toDto(agentId: String) = AgentSummaryDto(
    id = agentId,
    buildVersion = buildVersion,
    name = name,
    summary = toDto()
)

internal fun AgentSummary.toDto() = SummaryDto(
    coverage = coverage.percentage(),
    coverageCount = coverage,
    methodCount = methodCount,
    scopeCount = scopeCount,
    arrow = arrow,
    risks = riskCounts.total, //TODO remove after changes on frontend
    riskCounts = riskCounts,
    testDuration = testDuration,
    tests = toTestTypeSummary(),
    testsToRun = testsToRun.toTestCountDto(),
    recommendations = recommendations()
)

internal operator fun AgentSummary.plus(
    other: AgentSummary
): AgentSummary = copy(
    coverage = coverage + other.coverage,
    methodCount = methodCount + other.methodCount,
    coverageByType = coverageByType.merge(other.coverageByType) { count1, count2 ->
        count1 + count2
    },
    scopeCount = scopeCount + other.scopeCount,
    arrow = ArrowType.UNCHANGED,
    risks = emptyMap(),
    riskCounts = riskCounts + other.riskCounts,
    testDuration = testDuration + other.testDuration,
    tests = tests.merge(other.tests, ::mergeDistinct),
    testsToRun = testsToRun.merge(other.testsToRun, ::mergeDistinct),
    durationByType = durationByType.merge(other.durationByType) { duration1, duration2 ->
        duration1 + duration2
    }
)

operator fun Count.plus(other: Count): Count = copy(
    covered = covered + other.covered,
    total = total + other.total
)

private fun GroupedTests.toTestCountDto() = TestCountDto(
    count = totalCount(),
    byType = mapValues { it.value.count() }
)

private operator fun RiskCounts.plus(other: RiskCounts) = RiskCounts(
    new = new + other.new,
    modified = modified + other.modified,
    total = total + other.total
)

private fun AgentSummary.toTestTypeSummary() = coverageByType.map { (type, count) ->
    count.copy(total = coverage.total).let {
        TestTypeSummary(
            type = type,
            summary = TestSummary(
                coverage = CoverDto(
                    percentage = it.percentage(),
                    count = it
                ),
                testCount = tests[type]?.count() ?: 0,
                duration = durationByType[type] ?: 0L
            )
        )
    }
}

private fun <K, V> Map<K, V>.merge(
    other: Map<K, V>,
    operation: (V, V) -> V
): Map<K, V> = mapValuesTo(mutableMapOf<K, V>()) { (k, v) ->
    other[k]?.let { operation(v, it) } ?: v
}.also { map ->
    other.forEach { (k, v) ->
        if (k !in map) {
            map[k] = v
        }
    }
}

private fun mergeDistinct(
    list1: List<String>,
    list2: List<String>
): List<String> = sequenceOf(list1, list2).map { it.asSequence() }.flatten().distinct().toList()
