package com.epam.drill.plugins.test2code

import com.epam.drill.common.*
import com.epam.drill.plugins.test2code.api.*

internal fun Iterable<Method>.diff(other: Iterable<Method>): DiffMethods = run {
    //TODO get rid of common Method usages
    val otherDeclarations = other.map { Triple(it.ownerClass, it.name, it.desc) }
    val newAndModified = subtract(other)
    val modified = newAndModified.filterTo(mutableSetOf()) {
        Triple(it.ownerClass, it.name, it.desc) in otherDeclarations
    }
    val modifiedDeclarations = modified.map { Triple(it.ownerClass, it.name, it.desc) }
    val unaffected = intersect(other)
    DiffMethods(
        new = newAndModified.subtract(modified),
        modified = modified,
        unaffected = unaffected,
        deleted = other.subtract(unaffected).filterTo(mutableSetOf()) {
            Triple(it.ownerClass, it.name, it.desc) !in modifiedDeclarations
        }
    )
}

fun BuildMethods.toSummaryDto() = MethodsSummaryDto(
    all = totalMethods.run { Count(coveredCount, totalCount) },
    new = newMethods.run { Count(coveredCount, totalCount) },
    modified = allModifiedMethods.run { Count(coveredCount, totalCount) },
    unaffected = unaffectedMethods.run { Count(coveredCount, totalCount) },
    deleted = Count(deletedCoveredMethodsCount, deletedMethods.totalCount),
    risks = toRiskSummaryDto()
)

fun BuildMethods.toRiskSummaryDto() = RiskSummaryDto(
    total = newMethods.run { totalCount - coveredCount } + allModifiedMethods.run { totalCount - coveredCount },
    new = newMethods.run { totalCount - coveredCount },
    modified = allModifiedMethods.run { totalCount - coveredCount }
)
