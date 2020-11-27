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
    val coverageByType: CoverageByType,
    val methodCount: Count,
    val scopeCount: Int,
    val arrow: ArrowType,
    val riskCounts: RiskCounts = RiskCounts(),
    val risks: TypedRisks,
    val tests: GroupedTests,
    val testsToRun: GroupedTests
)

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
    parentCoverageCount: Count? = null
): AgentSummary = AgentSummary(
    name = agentName,
    buildVersion = version,
    coverage = coverage.count,
    methodCount = coverage.methodCount,
    coverageByType = coverage.byTestType,
    scopeCount = coverage.scopeCount,
    arrow = parentCoverageCount.arrowType(coverage.count),
    risks = risks,
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
    tests = coverageByType.toTestTypeSummary(tests),
    testsToRun = testsToRun.toTestCountDto(),
    recommendations = recommendations()
)

internal operator fun AgentSummary.plus(
    other: AgentSummary
): AgentSummary = copy(
    coverage = coverage + other.coverage,
    methodCount = methodCount + other.methodCount,
    coverageByType = coverageByType + other.coverageByType,
    scopeCount = scopeCount + other.scopeCount,
    arrow = ArrowType.UNCHANGED,
    risks = emptyMap(),
    riskCounts = riskCounts + other.riskCounts,
    tests = tests + other.tests,
    testsToRun = testsToRun + other.testsToRun
)

operator fun Count.plus(other: Count): Count = copy(
    covered = covered + other.covered,
    total = total + other.total
)

private fun GroupedTests.toTestCountDto() = TestCountDto(
    count = totalCount(),
    byType = mapValues { it.value.count() }
)

private operator fun GroupedTests.plus(other: GroupedTests): GroupedTests = sequenceOf(this, other)
    .flatMap { it.asSequence() }
    .groupBy({ it.key }, { it.value })
    .mapValues { (_, values) ->
        values.flatten().distinct()
    }

private operator fun RiskCounts.plus(other: RiskCounts) = RiskCounts(
    new = new + other.new,
    modified = modified + other.modified,
    total = total + other.total
)

private fun CoverageByType.toTestTypeSummary(tests: GroupedTests) = map { (type, count) ->
    TestTypeSummary(
        type = type,
        summary = TestSummary(
            coverage = CoverDto(
                percentage = count.percentage(),
                count = count
            ),
            testCount = tests[type]?.count() ?: 0
        )
    )
}
