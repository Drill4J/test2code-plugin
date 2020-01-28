package com.epam.drill.plugins.test2code

import com.epam.kodux.*

operator fun SummaryDto.plus(other: Any): SummaryDto = when (other) {
    is SummaryDto -> plus(other)
    else -> this
}

private fun SummaryDto.plus(other: SummaryDto): SummaryDto {
    val aggCoverages = _aggCoverages + other._aggCoverages
    return copy(
        coverage = aggCoverages.average(),
        arrow = null,
        risks = risks + other.risks,
        testsToRun = testsToRun + other.testsToRun,
        _aggCoverages = aggCoverages
    )
}

operator fun TestsToRunDto.plus(other: Any): TestsToRunDto = when (other) {
    is TestsToRunDto -> plus(other)
    else -> this
}

private fun TestsToRunDto.plus(other: TestsToRunDto): TestsToRunDto {
    val mergedGroupedTests = sequenceOf(groupedTests, other.groupedTests)
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
