package com.epam.drill.plugins.test2code

import com.epam.kodux.*

suspend fun StoreClient.summaryOf(agentId: String, buildVersion: String): SummaryDto? {
    return readLastBuildCoverage(agentId, buildVersion)?.toSummary()
}

private fun LastBuildCoverage.toSummary() = SummaryDto(
    coverage = coverage,
    arrow = arrow?.let { ArrowType.valueOf(it) },
    risks = risks,
    testsToRunDto = testsToRunDto,
    _aggCoverages = listOf(coverage)
)
