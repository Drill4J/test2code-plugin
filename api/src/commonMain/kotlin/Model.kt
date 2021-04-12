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
package com.epam.drill.plugins.test2code.api

import com.epam.drill.plugins.test2code.util.*
import kotlinx.serialization.*

@Serializable
data class StartPayload(
    val testType: String = "MANUAL",
    val sessionId: String = "",
    val testName: String? = null,
    val isRealtime: Boolean = false,
    val isGlobal: Boolean = false,
) : JvmSerializable

@Serializable
data class SessionPayload(val sessionId: String) : JvmSerializable

@Serializable
data class SessionDataPayload(val sessionId: String, val data: String) : JvmSerializable

@Serializable
data class CoverPayload(
    val sessionId: String,
    val data: List<EntityProbes>,
) : JvmSerializable

@Serializable
class EntityProbes(
    val name: String,
    val test: String = "",
    val probes: List<Boolean>,
) : JvmSerializable

@Serializable
data class StopSessionPayload(
    val sessionId: String,
    val testRun: TestRun? = null,
) : JvmSerializable

@Serializable
data class AddTestsPayload(
    val sessionId: String,
    val testRun: TestRun? = null,
) : JvmSerializable

@Serializable
data class TestRun(
    val name: String = "",
    val startedAt: Long,
    val finishedAt: Long,
    val tests: List<TestInfo> = emptyList(),
) : JvmSerializable

@Serializable
data class TestInfo(
    val name: String,
    val result: TestResult,
    val startedAt: Long,
    val finishedAt: Long,
) : JvmSerializable

enum class TestResult : JvmSerializable {
    PASSED,
    FAILED,
    ERROR,
    SKIPPED,
    UNKNOWN
}

@Serializable
data class ActiveScopeChangePayload(
    val scopeName: String,
    val savePrevScope: Boolean = false,
    val prevScopeEnabled: Boolean = true,
) : JvmSerializable

@Serializable
data class RenameScopePayload(
    val scopeId: String,
    val scopeName: String,
) : JvmSerializable

@Serializable
data class ScopePayload(val scopeId: String = "") : JvmSerializable

@Serializable
data class BuildVersionDto(
    val version: String,
)

@Serializable
data class CoverMethod(
    val ownerClass: String,
    val name: String,
    val desc: String,
    val hash: String = "",
    val count: Count,
    val coverageRate: CoverageRate = CoverageRate.MISSED,
) : JvmSerializable

enum class CoverageRate : JvmSerializable {
    MISSED,
    PARTLY,
    FULL
}

@Serializable
data class RiskCounts(
    val new: Int = 0,
    val modified: Int = 0,
    val total: Int = 0,
) : JvmSerializable

@Serializable
data class TestTypeSummary(
    val type: String,
    val summary: TestSummary,
) : JvmSerializable

interface Coverage {
    val percentage: Double
    val count: Count
    val methodCount: Count
    val classCount: Count
    val packageCount: Count
    val riskCount: Count
    val testTypeOverlap: CoverDto
    val byTestType: List<TestTypeSummary>
}

@Serializable
data class ScopeCoverage(
    override val percentage: Double = 0.0,
    override val count: Count = zeroCount,
    val overlap: CoverDto = CoverDto(),
    override val methodCount: Count = zeroCount,
    override val classCount: Count = zeroCount,
    override val packageCount: Count = zeroCount,
    override val riskCount: Count = zeroCount,
    override val testTypeOverlap: CoverDto = CoverDto(),
    override val byTestType: List<TestTypeSummary> = emptyList(),
) : Coverage, JvmSerializable


@Serializable
data class BuildCoverage(
    override val percentage: Double,
    override val count: Count,
    override val methodCount: Count,
    override val classCount: Count,
    override val packageCount: Count,
    override val riskCount: Count = zeroCount,
    override val testTypeOverlap: CoverDto = CoverDto(),
    override val byTestType: List<TestTypeSummary> = emptyList(),
    val finishedScopesCount: Int = 0,
) : Coverage, JvmSerializable

enum class ArrowType : JvmSerializable {
    INCREASE,
    DECREASE,
    UNCHANGED
}

@Serializable
data class MethodsSummaryDto(
    val all: Count,
    val new: Count,
    val modified: Count,
    val unaffected: Count,
    val deleted: Count,
    val risks: RiskCounts = RiskCounts(),
) : JvmSerializable

@Serializable
data class MethodsInfo(
    val totalCount: Int = 0,
    val coveredCount: Int = 0,
    val methods: List<CoverMethod> = emptyList(),
)

@Serializable
data class PackageTree(
    val totalCount: Int = 0,
    val totalMethodCount: Int = 0,
    val totalClassCount: Int = 0,
    val packages: List<JavaPackageCoverage> = emptyList(),
) : JvmSerializable

@Serializable
data class JavaPackageCoverage(
    @StringIntern
    val id: String,
    @StringIntern
    val name: String,
    val totalClassesCount: Int = 0,
    val totalMethodsCount: Int = 0,
    val totalCount: Int = 0,
    val coverage: Double = 0.0,
    val coveredClassesCount: Int = 0,
    val coveredMethodsCount: Int = 0,
    val assocTestsCount: Int = 0,
    val classes: List<JavaClassCoverage> = emptyList(),
) : JvmSerializable

@Serializable
data class JavaClassCoverage(
    @StringIntern
    val id: String,
    @StringIntern
    val name: String,
    @StringIntern
    val path: String,
    val totalMethodsCount: Int = 0,
    val totalCount: Int = 0,
    val coverage: Double = 0.0,
    val coveredMethodsCount: Int = 0,
    val assocTestsCount: Int = 0,
    val methods: List<JavaMethodCoverage> = emptyList(),
    val probes: List<Int> = emptyList(),
) : JvmSerializable

@Serializable
data class JavaMethodCoverage(
    @StringIntern
    val id: String,
    @StringIntern
    val name: String,
    @StringIntern
    val desc: String,
    @StringIntern
    val decl: String,
    val count: Int,
    val coverage: Double = 0.0,
    val assocTestsCount: Int = 0,
    val probeRange: ProbeRange = ProbeRange.EMPTY,
) : JvmSerializable

@Serializable
data class ProbeRange(val first: Int, val last: Int) : JvmSerializable {
    companion object {
        val EMPTY = ProbeRange(1, 0)
    }
}

@Serializable
data class AssociatedTests(
    @StringIntern
    val id: String,
    @StringIntern
    val packageName: String = "",
    @StringIntern
    val className: String = "",
    @StringIntern
    val methodName: String = "",
    val tests: List<TypedTest> = emptyList(),
) : JvmSerializable {
    override fun equals(other: Any?) = other is AssociatedTests && id == other.id && tests.containsAll(other.tests)
    override fun hashCode() = id.hashCode()
}

@Serializable
data class TypedTest(
    @StringIntern
    val name: String,
    @StringIntern
    val type: String,
) : JvmSerializable

//TODO remove this from the api and refactor after changes on the frontend
@Serializable
data class MethodsCoveredByTest(
    val id: String,
    val testName: String,
    val testType: String,
    val allMethods: List<CoverMethod> = emptyList(),
    val newMethods: List<CoverMethod> = emptyList(),
    val modifiedMethods: List<CoverMethod> = emptyList(),
    val unaffectedMethods: List<CoverMethod> = emptyList(),
) : JvmSerializable

@Serializable
data class CoveredMethodCounts(
    val all: Int,
    val new: Int,
    val modified: Int,
    val unaffected: Int,
) : JvmSerializable

@Serializable
data class CoverageByTests(
    val all: TestSummary,
    val byType: List<TestTypeSummary>,
) : JvmSerializable

@Serializable
data class TestedMethodsSummary(
    val id: String,
    val testName: String,
    val testType: String,
    val methodCounts: CoveredMethodCounts,
) : JvmSerializable

@Serializable
data class TestCoverageDto(
    val id: String,
    val type: String,
    val name: String,
    val toRun: Boolean = false,
    val coverage: CoverDto = CoverDto(),
    val stats: TestStats = TestStats(0, TestResult.PASSED),
) : JvmSerializable

@Serializable
data class TestStats(
    val duration: Long,
    val result: TestResult,
) : JvmSerializable

@Serializable
data class ActiveSessions(
    val count: Int,
    val testTypes: Set<String> = emptySet(),
) : JvmSerializable

@Serializable
data class ActiveSessionDto(
    val id: String,
    val agentId: String,
    val testType: String,
    val isGlobal: Boolean,
    val isRealtime: Boolean,
) : JvmSerializable

@Serializable
data class ScopeSummary(
    val name: String,
    val id: String,
    val started: Long,
    val finished: Long = 0L,
    var enabled: Boolean = true,
    val active: Boolean = true,
    val sessionsFinished: Int = 0,
    val coverage: ScopeCoverage = ScopeCoverage(),
) : JvmSerializable

const val zeroCount: Count = 0

@Serializable
data class TestCountDto(
    val count: Int = 0,
    val byType: Map<String, Int> = emptyMap(),
) : JvmSerializable

@Serializable
data class TestSummary(
    val coverage: CoverDto,
    val testCount: Int = 0,
    val duration: Long = 0L,
) : JvmSerializable

@Serializable
data class TestsToRunSummaryDto(
    val buildVersion: String,
    val stats: TestsToRunStatsDto,
    val statsByType: Map<String, TestsToRunStatsDto> = emptyMap(),
) : JvmSerializable

@Serializable
data class TestsToRunStatsDto(
    val total: Int,
    val completed: Int,
    val duration: Long,
    val parentDuration: Long,
) : JvmSerializable

@Serializable
data class RiskDto(
    val ownerClass: String,
    val name: String,
    val desc: String,
    val type: RiskType,
) : JvmSerializable

enum class RiskType : JvmSerializable {
    NEW, MODIFIED
}

typealias GroupedTests = Map<String, List<String>>

@Serializable
data class GroupedTestsDto(
    val byType: GroupedTests = emptyMap(),
    val totalCount: Int = 0,
) : JvmSerializable

@Serializable
data class BuildStatsDto(
    val parentVersion: String = "",
    val total: Int = 0,
    val new: Int = 0,
    val modified: Int = 0,
    val unaffected: Int = 0,
    val deleted: Int = 0,
) : JvmSerializable

@Serializable
data class SummaryDto(
    val coverage: Double = 0.0,
    val coverageCount: Count = zeroCount,
    val methodCount: Count = zeroCount,
    val scopeCount: Int = 0,
    val arrow: ArrowType = ArrowType.UNCHANGED,
    val risks: Int = 0, //TODO remove after changes on frontend
    val riskCounts: RiskCounts = RiskCounts(),
    val testDuration: Long,
    val tests: List<TestTypeSummary> = emptyList(),
    val testsToRun: TestCountDto = TestCountDto(),
    val recommendations: Set<String> = emptySet(),
) : JvmSerializable

@Serializable
data class AgentSummaryDto(
    val id: String,
    val name: String,
    val buildVersion: String,
    val summary: SummaryDto,
) : JvmSerializable

@Serializable
data class ServiceGroupSummaryDto(
    val name: String,
    val aggregated: SummaryDto,
    val summaries: List<AgentSummaryDto> = emptyList(),
) : JvmSerializable

@Serializable
data class CoverDto(
    val percentage: Double = 0.0,
    val methodCount: Count = zeroCount,
    val count: Count = zeroCount,
) : JvmSerializable

//@Serializable
//data class Count(
//    val covered: Int,
//    val total: Int
//)

typealias Count = Long

val Count.covered
    get() = (this shr 32).toInt()

val Count.total
    get() = this.toInt()


fun Count.copy(covered: Int = (this shr 32).toInt(), total: Int = this.toInt()): Count {
    return Count(covered, total)
}

fun Count(covered: Int, total: Int): Count {
    return (covered.toLong() shl 32) or (total.toLong() and 0xFFFFFFFFL)
}


expect interface JvmSerializable
//@Serializable
//inline class Count(val value: Long) {
//    val covered
//        get() = (value shr 32).toInt()
//
//    val total
//        get() = value.toInt()
//
//    fun copy(covered: Int = (value shr 32).toInt(), total: Int = value.toInt()): Count {
//        return Count(covered, total)
//    }
//}
//
//fun Count(covered: Int, total: Int): Count {
//    return Count((covered.toLong() shl 32) or (total.toLong() and 0xFFFFFFFFL))
//}
