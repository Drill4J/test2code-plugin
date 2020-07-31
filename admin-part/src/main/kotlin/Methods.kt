package com.epam.drill.plugins.test2code

import com.epam.drill.plugins.test2code.api.*
import kotlinx.serialization.*

@Serializable
internal data class Method(
    val ownerClass: String,
    val name: String,
    val desc: String,
    val hash: String
) : Comparable<Method> {
    override fun compareTo(
        other: Method
    ): Int = ownerClass.compareTo(other.ownerClass).takeIf {
        it != 0
    } ?: name.compareTo(other.name).takeIf {
        it != 0
    } ?: desc.compareTo(other.desc)
}

internal fun List<Method>.diff(otherMethods: List<Method>): DiffMethods = if (any()) {
    if (otherMethods.any()) {
        val new = mutableListOf<Method>()
        val modified = mutableListOf<Method>()
        val deleted = mutableListOf<Method>()
        val unaffected = mutableListOf<Method>()
        val otherItr = otherMethods.iterator()
        iterator().run {
            var lastRight: Method? = otherItr.next()
            while (hasNext()) {
                val left = next()
                if (lastRight == null) {
                    new.add(left)
                }
                while (lastRight != null) {
                    val right = lastRight
                    val cmp = left.compareTo(right)
                    if (cmp <= 0) {
                        when {
                            cmp == 0 -> {
                                (unaffected.takeIf { left.hash == right.hash } ?: modified).add(left)
                                lastRight = otherItr.nextOrNull()
                            }
                            cmp < 0 -> {
                                new.add(left)
                            }
                        }
                        break
                    }
                    deleted.add(right)
                    lastRight = otherItr.nextOrNull()
                    if (lastRight == null) {
                        new.add(left)
                    }
                }
            }
            lastRight?.let { deleted.add(it) }
            while (otherItr.hasNext()) {
                deleted.add(otherItr.next())
            }
        }
        DiffMethods(
            new = new,
            modified = modified,
            deleted = deleted,
            unaffected = unaffected
        )
    } else DiffMethods(new = this)
} else DiffMethods(deleted = otherMethods)

fun BuildMethods.toSummaryDto() = MethodsSummaryDto(
    all = totalMethods.run { Count(coveredCount, totalCount) },
    new = newMethods.run { Count(coveredCount, totalCount) },
    modified = allModifiedMethods.run { Count(coveredCount, totalCount) },
    unaffected = unaffectedMethods.run { Count(coveredCount, totalCount) },
    deleted = Count(deletedCoveredMethodsCount, deletedMethods.totalCount)
)

fun BuildMethods.toRiskSummaryDto() = RiskSummaryDto(
    total = newMethods.run { totalCount - coveredCount } + allModifiedMethods.run { totalCount - coveredCount },
    new = newMethods.run { totalCount - coveredCount },
    modified = allModifiedMethods.run { totalCount - coveredCount }
)

private fun <T> Iterator<T>.nextOrNull(): T? = if (hasNext()) {
    next()
} else null

fun MethodsCoveredByTest.toSummary() = TestedMethodsSummary(
    id = id,
    testName = testName,
    testType = testType,
    methodCounts = CoveredMethodCounts(
        all = allMethods.count(),
        modified = modifiedMethods.count(),
        unaffected = unaffectedMethods.count(),
        new = newMethods.count()
    )
)

fun MethodsCoveredByType.toSummary() = TestedMethodsByTypeSummary(
    testType = testType,
    testsCount = testsCount,
    methodCounts = CoveredMethodCounts(
        all = allMethods.count(),
        modified = modifiedMethods.count(),
        unaffected = unaffectedMethods.count(),
        new = newMethods.count()
    )
)
