/**
 * Copyright 2020 EPAM Systems
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.epam.drill.plugins.test2code

import com.epam.drill.plugins.test2code.api.*
import com.epam.drill.plugins.test2code.coverage.*
import com.epam.drill.plugins.test2code.util.*
import kotlinx.serialization.*

@Serializable
internal data class Method(
    @StringIntern
    val ownerClass: String,
    @StringIntern
    val name: String,
    @StringIntern
    val desc: String,
    @StringIntern
    val hash: String
) : Comparable<Method> , java.io.Serializable {
    override fun compareTo(
        other: Method
    ): Int = ownerClass.compareTo(other.ownerClass).takeIf {
        it != 0
    } ?: name.compareTo(other.name).takeIf {
        it != 0
    } ?: desc.compareTo(other.desc)
}

internal typealias TypedRisks = Map<RiskType, List<Method>>

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

internal fun BuildMethods.toSummaryDto() = MethodsSummaryDto(
    all = totalMethods.run { Count(coveredCount, totalCount) },
    new = newMethods.run { Count(coveredCount, totalCount) },
    modified = allModifiedMethods.run { Count(coveredCount, totalCount) },
    unaffected = unaffectedMethods.run { Count(coveredCount, totalCount) },
    deleted = deletedMethods.run { Count(coveredCount, totalCount) }
)

internal fun DiffMethods.risks(
    bundleCounter: BundleCounter
): TypedRisks = bundleCounter.coveredMethods(new + modified).let { covered ->
    mapOf(
        RiskType.NEW to new.filter { it !in covered },
        RiskType.MODIFIED to modified.filter { it !in covered }
    )
}

internal fun TypedRisks.toCounts() = RiskCounts(
    new = this[RiskType.NEW]?.count() ?: 0,
    modified = this[RiskType.MODIFIED]?.count() ?: 0
).run { copy(total = new + modified) }

internal fun TypedRisks.toListDto(): List<RiskDto> = flatMap { (type, methods) ->
    methods.map { method ->
        RiskDto(
            type = type,
            ownerClass = method.ownerClass,
            name = method.name,
            desc = method.desc
        )
    }
}

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

private fun <T> Iterator<T>.nextOrNull(): T? = if (hasNext()) {
    next()
} else null
