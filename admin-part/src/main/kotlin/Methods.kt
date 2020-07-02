package com.epam.drill.plugins.test2code

import com.epam.drill.plugins.test2code.api.*

fun BuildMethods.toSummaryDto() = MethodsSummaryDto(
    all = totalMethods.run { Count(coveredCount, totalCount) },
    new = newMethods.run { Count(coveredCount, totalCount) },
    modified = allModifiedMethods.run { Count(coveredCount, totalCount) },
    unaffected = unaffectedMethods.run { Count(coveredCount, totalCount) },
    deleted = Count(deletedCoveredMethodsCount, deletedMethods.totalCount)
)
