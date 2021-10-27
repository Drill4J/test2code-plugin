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
package com.epam.drill.plugins.test2code.group

import com.epam.drill.plugins.test2code.*
import com.epam.drill.plugins.test2code.api.*
import com.epam.drill.plugins.test2code.coverage.*
import kotlinx.atomicfu.*
import kotlinx.collections.immutable.*

//TODO move agent summary out of the group package

internal data class AgentSummary(
    val name: String,
    val buildVersion: String,
    val coverage: Count,
    val coverageByType: Map<String, Count>,
    val methodCount: Count,
    val scopeCount: Int,
    val arrow: ArrowType,
    val riskCounts: RiskCounts = RiskCounts(),
    val risks: TypedRisks,
    val tests: GroupedTests,
    val testsCoverage: List<TestCoverageDto>,
    val testDuration: Long,
    val testsToRun: GroupedTests,
    val durationByType: GroupedDuration,
)
internal typealias GroupedDuration = Map<String, Long>

internal typealias AgentSummaries = PersistentMap<String, AgentSummary>

internal fun CachedBuild.toSummary(
    agentName: String,
    testsToRun: GroupedTests,
    risks: TypedRisks,
    coverageByTests: CoverageByTests? = null,
    testsCoverage: List<TestCoverageDto>? = null,
    parentCoverageCount: Count? = null,
): AgentSummary = AgentSummary(
    name = agentName,
    buildVersion = version,
    coverage = stats.coverage,
    methodCount = stats.methodCount,
    coverageByType = stats.coverageByType,
    scopeCount = stats.scopeCount,
    arrow = parentCoverageCount.arrowType(stats.coverage),
    risks = risks.notCovered(bundleCounters.all),
    testDuration = coverageByTests?.all?.duration ?: 0L,
    durationByType = coverageByTests?.byType?.groupBy { it.type }
        ?.mapValues { (_, values) -> values.sumOf { it.summary.duration } }
        ?: emptyMap(),
    tests = tests.tests,
    testsCoverage = testsCoverage ?: emptyList(),
    testsToRun = testsToRun.withoutCoverage(bundleCounters)
).let { it.copy(riskCounts = it.risks.toCounts()) }

internal fun AgentSummary.toDto(agentId: String) = AgentSummaryDto(
    id = agentId,
    buildVersion = buildVersion,
    name = name,
    summary = toDto()
)

internal fun AgentSummary.toDto() = SummaryDto(
    coverage = coverage.percentage(),
    coverageCount = coverage,
    methodCount = methodCount,
    scopeCount = scopeCount,
    arrow = arrow,
    risks = riskCounts.total, //TODO remove after changes on frontend
    riskCounts = riskCounts,
    testDuration = testDuration,
    tests = toTestTypeSummary(),
    testsToRun = testsToRun.toTestCountDto(),
    recommendations = recommendations()
)

private fun GroupedTests.toTestCountDto() = TestCountDto(
    count = totalCount(),
    byType = mapValues { it.value.count() }
)

private fun AgentSummary.toTestTypeSummary() = coverageByType.map { (type, count) ->
    count.copy(total = coverage.total).let {
        TestTypeSummary(
            type = type,
            summary = TestSummary(
                coverage = CoverDto(
                    percentage = it.percentage(),
                    count = it.toDto()
                ),
                testCount = tests[type]?.count() ?: 0,
                duration = durationByType[type] ?: 0L
            )
        )
    }
}

private fun <K, V> Map<K, V>.merge(
    other: Map<K, V>,
    operation: (V, V) -> V,
): Map<K, V> = mapValuesTo(mutableMapOf<K, V>()) { (k, v) ->
    other[k]?.let { operation(v, it) } ?: v
}.also { map ->
    other.forEach { (k, v) ->
        if (k !in map) {
            map[k] = v
        }
    }
}

private fun <T> mergeDistinct(
    list1: List<T>,
    list2: List<T>,
): List<T> = sequenceOf(list1, list2).map { it.asSequence() }.flatten().distinct().toList()
