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
import com.epam.kodux.*
import kotlinx.serialization.*

@Serializable
data class TestsToRunStats(
    val total: Int = 0,
    val completed: Int = 0,
    val duration: Long = 0L,
    val parentDuration: Long = 0L,
)

@Serializable
internal data class TestsToRunSummary(
    @Id val buildVersion: String,
    val parentVersion: String = "",
    val lastModifiedAt: Long = 0L,
    val stats: TestsToRunStats = TestsToRunStats(),
    val statsByType: Map<String, TestsToRunStats> = emptyMap(),
)

private fun CoverContext.toTestsToRunStats(
    parentDuration: Long,
    curTestsToRun: GroupedTests = testsToRun,
): TestsToRunStats = TestsToRunStats(
    total = curTestsToRun.totalCount(),
    completed = curTestsToRun.withCoverage(build.bundleCounters).totalCount(),
    duration = curTestsToRun.totalDuration(build.bundleCounters.byTestOverview),
    parentDuration = parentDuration
)

internal fun CoverContext.toTestsToRunSummary(): TestsToRunSummary = TestsToRunSummary(
    buildVersion = build.version,
    parentVersion = build.parentVersion,
    lastModifiedAt = currentTimeMillis(),
    stats = toTestsToRunStats(testsToRunParentDurations.all),
    statsByType = testsToRun.mapValues { (type, tests) ->
        toTestsToRunStats(
            parentDuration = testsToRunParentDurations.byType[type] ?: 0L,
            curTestsToRun = mapOf(type to tests)
        )
    }
)

private fun TestsToRunStats.toTestsToRunStatsDto() = TestsToRunStatsDto(
    total = total,
    completed = completed,
    duration = duration,
    parentDuration = parentDuration
)

internal fun TestsToRunSummary.toTestsToRunSummaryDto() = TestsToRunSummaryDto(
    buildVersion = buildVersion,
    stats = stats.toTestsToRunStatsDto(),
    statsByType = statsByType.mapValues { it.value.toTestsToRunStatsDto() }
)

internal suspend fun StoreClient.loadTestsToRunSummary(
    buildVersion: String,
    parentVersion: String = "",
): List<TestsToRunSummary> = getAll<TestsToRunSummary>()
    .filter { it.parentVersion == parentVersion && it.buildVersion != buildVersion }
    .sortedBy { it.lastModifiedAt }
