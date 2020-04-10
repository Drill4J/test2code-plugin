package com.epam.drill.plugins.test2code

import com.epam.drill.plugins.test2code.api.*
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
