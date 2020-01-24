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
    val mergedGroupedTests = (groupedTests.keys + testsToRun.groupedTests.keys)
        .associateWith {
            setOf(groupedTests[it], testsToRun.groupedTests[it])
                .filterNotNull()
                .flatten()
                .distinct()
        }
    return TestsToRunDto(mergedGroupedTests, mergedGroupedTests.sumSizeLists())
}

fun GroupedTests.sumSizeLists(): Int {
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
