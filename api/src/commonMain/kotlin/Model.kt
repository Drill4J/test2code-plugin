package com.epam.drill.plugins.test2code.api

import kotlinx.serialization.*

@Serializable
data class StartPayload(
    val testType: String = "MANUAL",
    val sessionId: String = "",
    val testName: String? = null,
    val isRealtime: Boolean = false,
    val isGlobal: Boolean = false
)

@Serializable
data class SessionPayload(val sessionId: String)

@Serializable
data class SessionDataPayload(val sessionId: String, val data: String)

@Serializable
data class CoverPayload(
    val sessionId: String,
    val data: List<EntityProbes>
)

@Serializable
class EntityProbes(
    val name: String,
    val test: String = "",
    val probes: List<Boolean>
)

@Serializable
data class StopSessionPayload(
    val sessionId: String,
    val testRun: TestRun? = null
)

@Serializable
data class TestRun(
    val name: String = "",
    val startedAt: Long,
    val finishedAt: Long,
    val tests: List<TestInfo> = emptyList()
)

@Serializable
data class TestInfo(
    val name: String,
    val result: TestResult,
    val startedAt: Long,
    val finishedAt: Long
)

enum class TestResult {
    PASSED,
    FAILED,
    ERROR,
    SKIPPED
}

@Serializable
data class ActiveScopeChangePayload(
    val scopeName: String,
    val savePrevScope: Boolean = false,
    val prevScopeEnabled: Boolean = true
)

@Serializable
data class RenameScopePayload(
    val scopeId: String,
    val scopeName: String
)

@Serializable
data class ScopePayload(val scopeId: String = "")

@Serializable
data class BuildVersionDto(
    val version: String
)

@Serializable
data class CoverMethod(
    val ownerClass: String,
    val name: String,
    val desc: String,
    val hash: String = "",
    val count: Count,
    val coverageRate: CoverageRate = CoverageRate.MISSED
)

enum class CoverageRate {
    MISSED,
    PARTLY,
    FULL
}

@Serializable
data class RiskCounts(
    val new: Int = 0,
    val modified: Int = 0,
    val total: Int = 0
)

@Serializable
data class TestTypeSummary(
    val type: String,
    val summary: TestSummary
)

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
    override val byTestType: List<TestTypeSummary> = emptyList()
) : Coverage


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
    val finishedScopesCount: Int = 0
) : Coverage

enum class ArrowType {
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
    val risks: RiskCounts = RiskCounts()
)

@Serializable
data class MethodsInfo(
    val totalCount: Int = 0,
    val coveredCount: Int = 0,
    val methods: List<CoverMethod> = emptyList()
)

@Serializable
data class PackageTree(
    val totalCount: Int = 0,
    val totalMethodCount: Int = 0,
    val totalClassCount: Int = 0,
    val packages: List<JavaPackageCoverage> = emptyList()
)

@Serializable
data class JavaPackageCoverage(
    val id: String,
    val name: String,
    val totalClassesCount: Int = 0,
    val totalMethodsCount: Int = 0,
    val totalCount: Int = 0,
    val coverage: Double = 0.0,
    val coveredClassesCount: Int = 0,
    val coveredMethodsCount: Int = 0,
    val assocTestsCount: Int = 0,
    val classes: List<JavaClassCoverage> = emptyList()
)

@Serializable
data class JavaClassCoverage(
    val id: String,
    val name: String,
    val path: String,
    val totalMethodsCount: Int = 0,
    val totalCount: Int = 0,
    val coverage: Double = 0.0,
    val coveredMethodsCount: Int = 0,
    val assocTestsCount: Int = 0,
    val methods: List<JavaMethodCoverage> = emptyList(),
    val probes: List<Int> = emptyList()
)

@Serializable
data class JavaMethodCoverage(
    val id: String,
    val name: String,
    val desc: String,
    val decl: String,
    val count: Int,
    val coverage: Double = 0.0,
    val assocTestsCount: Int = 0,
    val probeRange: ProbeRange = ProbeRange.EMPTY
)

@Serializable
data class ProbeRange(val first: Int, val last: Int) {
    companion object {
        val EMPTY = ProbeRange(1, 0)
    }
}

@Serializable
data class AssociatedTests(
    val id: String,
    val packageName: String = "",
    val className: String = "",
    val methodName: String = "",
    val tests: List<TypedTest> = emptyList()
) {
    override fun equals(other: Any?) = other is AssociatedTests && id == other.id && tests.containsAll(other.tests)
    override fun hashCode() = id.hashCode()
}

@Serializable
data class TypedTest(
    val name: String,
    val type: String
)

//TODO remove this from the api and refactor after changes on the frontend
@Serializable
data class MethodsCoveredByTest(
    val id: String,
    val testName: String,
    val testType: String,
    val allMethods: List<CoverMethod> = emptyList(),
    val newMethods: List<CoverMethod> = emptyList(),
    val modifiedMethods: List<CoverMethod> = emptyList(),
    val unaffectedMethods: List<CoverMethod> = emptyList()
)

@Serializable
data class CoveredMethodCounts(
    val all: Int,
    val new: Int,
    val modified: Int,
    val unaffected: Int
)

@Serializable
data class CoverageByTests(
    val all: TestSummary,
    val byType: List<TestTypeSummary>
)

@Serializable
data class TestedMethodsSummary(
    val id: String,
    val testName: String,
    val testType: String,
    val methodCounts: CoveredMethodCounts
)

@Serializable
data class TestedMethodsByTypeSummary(
    val testType: String,
    val testsCount: Int,
    val methodCounts: CoveredMethodCounts
)

@Serializable
data class TestUsagesInfo(
    val id: String,
    val testName: String,
    val methodCalls: Int,
    val coverage: Double,
    val stats: TestStats
)

@Serializable
data class TestCoverageDto(
    val id: String,
    val type: String,
    val name: String,
    val toRun: Boolean = false,
    val coverage: CoverDto = CoverDto(),
    val stats: TestStats = TestStats(0, TestResult.PASSED)
)

@Serializable
data class TestStats(
    val duration: Long,
    val result: TestResult
)

@Serializable
data class TestsUsagesInfoByType(
    val testType: String,
    val coverage: Double,
    val methodsCount: Int,
    val tests: List<TestUsagesInfo> = emptyList()
)

@Serializable
data class ActiveSessions(
    val count: Int,
    val testTypes: Set<String> = emptySet()
)

@Serializable
data class ActiveSessionDto(
    val id: String,
    val agentId: String,
    val testType: String,
    val isGlobal: Boolean,
    val isRealtime: Boolean
)

@Serializable
data class ScopeSummary(
    val name: String,
    val id: String,
    val started: Long,
    val finished: Long = 0L,
    var enabled: Boolean = true,
    val active: Boolean = true,
    val sessionsFinished: Int = 0,
    val coverage: ScopeCoverage = ScopeCoverage()
)

val zeroCount = Count(covered = 0, total = 0)

@Serializable
data class TestCountDto(
    val count: Int = 0,
    val byType: Map<String, Int> = emptyMap()
)

@Serializable
data class TestSummary(
    val coverage: CoverDto,
    val testCount: Int = 0,
    val duration: Long = 0L
)

@Serializable
data class TestsToRunSummaryDto(
    val buildVersion: String,
    val stats: TestsToRunStatsDto,
    val statsByType: Map<String, TestsToRunStatsDto> = emptyMap()
)

@Serializable
data class TestsToRunStatsDto(
    val total: Int,
    val completed: Int,
    val duration: Long,
    val parentDuration: Long
)

@Serializable
data class RiskDto(
    val ownerClass: String,
    val name: String,
    val desc: String,
    val type: RiskType
)

enum class RiskType {
    NEW, MODIFIED
}

typealias GroupedTests = Map<String, List<String>>

@Serializable
data class GroupedTestsDto(
    val byType: GroupedTests = emptyMap(),
    val totalCount: Int = 0
)

@Serializable
data class BuildStatsDto(
    val parentVersion: String = "",
    val total: Int = 0,
    val new: Int = 0,
    val modified: Int = 0,
    val unaffected: Int = 0,
    val deleted: Int = 0
)

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
    val recommendations: Set<String> = emptySet()
)

@Serializable
data class AgentSummaryDto(
    val id: String,
    val name: String,
    val buildVersion: String,
    val summary: SummaryDto
)

@Serializable
data class ServiceGroupSummaryDto(
    val name: String,
    val aggregated: SummaryDto,
    val summaries: List<AgentSummaryDto> = emptyList()
)

@Serializable
data class CoverDto(
    val percentage: Double = 0.0,
    val methodCount: Count = zeroCount,
    val count: Count = zeroCount
)

@Serializable
data class Count(
    val covered: Int,
    val total: Int
)
