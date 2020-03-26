package com.epam.drill.plugins.test2code

import com.epam.drill.plugins.test2code.api.*
import com.epam.kodux.*

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
