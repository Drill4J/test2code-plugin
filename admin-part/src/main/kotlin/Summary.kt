package com.epam.drill.plugins.test2code

import com.epam.drill.plugins.test2code.api.*
import kotlinx.atomicfu.*
import kotlinx.collections.immutable.*

typealias AgentSummary = PersistentMap<String, SummaryDto>

val aggregator = Aggregator()

class Aggregator : (String, String, SummaryDto) -> SummaryDto? {
    private val _summaryStorage = atomic(persistentHashMapOf<String, AgentSummary>())

    override operator fun invoke(
        serviceGroup: String,
        agentId: String,
        summary: SummaryDto
    ): SummaryDto? {
        val storage = _summaryStorage.updateAndGet {
            val curAgentSummary = it[serviceGroup] ?: persistentHashMapOf()
            it.put(serviceGroup, curAgentSummary.plus(agentId to summary))
        }
        return storage[serviceGroup]
            ?.values
            ?.reduce { acc, element -> acc + element }
    }
}

internal fun Coverage.toSummaryDto(
    risks: Risks,
    testsToRun: GroupedTests
): SummaryDto = SummaryDto(
    coverage = count.percentage(),
    coverageCount = count,
    arrow = null,
    risks = risks.run { newMethods.count() + modifiedMethods.count() },
    testsToRun = TestsToRunDto(
        groupedTests = testsToRun,
        count = testsToRun.totalCount()
    )
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

