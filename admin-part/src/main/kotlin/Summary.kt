package com.epam.drill.plugins.test2code

import com.epam.kodux.*

operator fun SummaryDto?.plus(other: SummaryDto): SummaryDto = when (this) {
    null -> other
    else -> {
        val aggCoverages = _aggCoverages + other._aggCoverages
        copy(
            coverage = aggCoverages.average(),
            arrow = null,
            risks = risks + other.risks,
            testsToRun = testsToRun + other.testsToRun,
            _aggCoverages = aggCoverages
        )
    }
}

private operator fun TestsToRunDto.plus(testsToRun: TestsToRunDto): TestsToRunDto {
    val mergedGroupedTests = sequenceOf(groupedTests, testsToRun.groupedTests)
        .flatMap { it.asSequence() }
        .groupBy({ it.key }, { it.value })
        .mapValues { (_, values) ->
            values.flatten().distinct()
        }
    return TestsToRunDto(mergedGroupedTests, mergedGroupedTests.totalCount())
}

fun GroupedTests.totalCount(): Int {
    return this.values.sumBy { it.count() }
}

suspend fun StoreClient.summaryOf(agentId: String, buildVersion: String): SummaryDto? {
    return readLastBuildCoverage(agentId, buildVersion)?.toSummary()
}

private fun LastBuildCoverage.toSummary() = SummaryDto(
    coverage = coverage,
    arrow = arrow?.let { ArrowType.valueOf(it) },
    risks = risks,
    testsToRun = testsToRun,
    _aggCoverages = listOf(coverage)
)
