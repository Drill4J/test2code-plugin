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
import com.epam.drill.plugins.test2code.common.api.*
import com.epam.drill.plugins.test2code.common.api.JvmSerializable
import com.epam.drill.plugins.test2code.coverage.*
import com.epam.drill.plugins.test2code.group.*
import com.epam.kodux.*
import kotlinx.collections.immutable.*
import kotlinx.serialization.*

internal data class CachedBuild(
    val version: String,
    val parentVersion: String = "",
    val probes: PersistentMap<Long, ExecClassData> = persistentHashMapOf(),
    val bundleCounters: BundleCounters = BundleCounters.empty,
    val stats: BuildStats = BuildStats(version),
    val tests: BuildTests = BuildTests(),
)

@Serializable
internal data class BuildStats(
    @Id val version: String,
    val coverage: Count = zeroCount,
    val methodCount: Count = zeroCount,
    val coverageByType: Map<String, Count> = emptyMap(),
    val scopeCount: Int = 0,
) : JvmSerializable

internal fun BuildCoverage.toCachedBuildStats(
    context: CoverContext,
): BuildStats = context.build.stats.copy(
    coverage = count,
    methodCount = methodCount,
    coverageByType = byTestType.map {
        it.type to Count(
            it.summary.coverage.count.covered,
            it.summary.coverage.count.total
        )
    }.toMap(),
    scopeCount = finishedScopesCount
)

internal fun AgentSummary.recommendations(): Set<String> = sequenceOf(
    "Run recommended tests to cover modified methods".takeIf { testsToRun.any() },
    "Update your tests to cover risks".takeIf { riskCounts.total > 0 }
).filterNotNullTo(mutableSetOf())

internal fun CoverContext.toBuildStatsDto(): BuildStatsDto = BuildStatsDto(
    parentVersion = parentBuild?.version ?: "",
    total = methods.count(),
    new = methodChanges.new.count(),
    modified = methodChanges.modified.count(),
    unaffected = methodChanges.unaffected.count(),
    deleted = methodChanges.deleted.count()
)
