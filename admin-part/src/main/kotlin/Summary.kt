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
): SummaryDto {
    return SummaryDto(
        coverage = methodCount.percentage(),
        methodCount = methodCount,//todo make right coverage https://jiraeu.epam.com/browse/EPMDJ-2411
        arrow = null,
        risks = risks.run { newMethods.count() + modifiedMethods.count() },
        testsToRun = TestsToRunDto(
            groupedTests = testsToRun,
            count = testsToRun.totalCount()
        )
    )
}

operator fun SummaryDto.plus(other: SummaryDto): SummaryDto {
    val newCount = methodCount + other.methodCount
    return copy(
        coverage = newCount.percentage(),
        methodCount = newCount,
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

