package com.epam.drill.plugins.test2code

import com.epam.drill.plugins.test2code.api.*
import com.epam.drill.plugins.test2code.coverage.*
import kotlinx.atomicfu.*
import kotlinx.collections.immutable.*

typealias AgentSummary = PersistentMap<String, AgentSummaryDto>

val aggregator = Aggregator()

class Aggregator : (String, AgentSummaryDto) -> SummaryDto? {
    private val _summaryStorage = atomic(persistentHashMapOf<String, AgentSummary>())

    override operator fun invoke(
        serviceGroup: String,
        agentSummary: AgentSummaryDto
    ): SummaryDto? {
        val storage = _summaryStorage.updateAndGet {
            val curAgentSummary = it[serviceGroup] ?: persistentHashMapOf()
            it.put(serviceGroup, curAgentSummary.plus(agentSummary.id to agentSummary))
        }
        return storage[serviceGroup]
            ?.values
            ?.map { it.summary }
            ?.reduce { acc, element -> acc + element }
    }

    fun getSummaries(serviceGroup: String): List<AgentSummaryDto>? =
        _summaryStorage.value[serviceGroup]?.values?.toList()
}

internal fun CachedBuildCoverage.toSummaryDto(
    buildTests: BuildTests
): SummaryDto = SummaryDto(
    coverage = count.percentage(),
    coverageCount = count,
    arrow = arrow?.let { ArrowType.valueOf(it) },
    risks = riskCount.run { total - covered },
    testsToRun = buildTests.run {
        TestsToRunDto(
            groupedTests = testsToRun,
            count = testsToRun.totalCount()
        )
    }
)

operator fun SummaryDto.plus(other: SummaryDto): SummaryDto {
    val coverageCount = coverageCount + other.coverageCount
    return copy(
        coverage = coverageCount.percentage(),
        coverageCount = coverageCount,
        arrow = null,
        risks = risks + other.risks,
        testsToRun = testsToRun + other.testsToRun
    )
}

operator fun Count.plus(other: Count): Count = copy(
    covered = covered + other.covered,
    total = total + other.total
)

operator fun TestsToRunDto.plus(other: TestsToRunDto): TestsToRunDto {
    val mergedGroupedTests = sequenceOf(groupedTests, other.groupedTests)
        .flatMap { it.asSequence() }
        .groupBy({ it.key }, { it.value })
        .mapValues { (_, values) ->
            values.flatten().distinct()
        }
    return TestsToRunDto(mergedGroupedTests, mergedGroupedTests.totalCount())
}

fun GroupedTests.totalCount(): Int = this.values.sumBy { it.count() }
