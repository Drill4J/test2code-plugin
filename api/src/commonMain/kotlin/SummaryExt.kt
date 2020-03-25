package com.epam.drill.plugins.test2code.api

//TODO separate aggregation implementation from model classes, after separation remove this file

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
