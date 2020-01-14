package com.epam.drill.plugins.test2code

import com.epam.kodux.*

private val emptySummary = SummaryDto(
    coverage = 0.0,
    arrow = null,
    risks = 0,
    testsToRun = 0,
    _aggCoverages = listOf(0.0)
)

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
