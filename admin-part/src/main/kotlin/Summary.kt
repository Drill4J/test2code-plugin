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
            testsToRunDto = testsToRunDto, //TODO EPMDJ-2220, e.g. testsToRunDto = testsToRunDto + other.testsToRunDto
            _aggCoverages = aggCoverages
        )
    }
}

suspend fun StoreClient.summaryOf(agentId: String, buildVersion: String): SummaryDto? {
    return readLastBuildCoverage(agentId, buildVersion)?.toSummary()
}

private fun LastBuildCoverage.toSummary() = SummaryDto(
    coverage = coverage,
    arrow = arrow?.let { ArrowType.valueOf(it) },
    risks = risks,
    testsToRunDto = testsToRun,
    _aggCoverages = listOf(coverage)
)
