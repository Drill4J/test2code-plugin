package com.epam.drill.plugins.test2code.api

import kotlinx.serialization.*

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
data class JavaMethod(
    val ownerClass: String,
    val name: String,
    val desc: String,
    val hash: String?,
    val coverageRate: CoverageRate = CoverageRate.MISSED
)

enum class CoverageRate {
    MISSED,
    PARTLY,
    FULL
}

interface Coverage {
    val ratio: Double
    val count: Count
    val methodCount: Count
    val riskCount: Count
    val byTestType: Map<String, TestTypeSummary>
}

@Serializable
data class ScopeCoverage(
    override val ratio: Double,
    override val count: Count,
    override val methodCount: Count,
    override val riskCount: Count,
    override val byTestType: Map<String, TestTypeSummary>
) : Coverage


@Serializable
data class BuildCoverage(
    override val ratio: Double,
    override val count: Count,
    override val methodCount: Count,
    override val riskCount: Count,
    override val byTestType: Map<String, TestTypeSummary>,
    val diff: Double,
    val prevBuildVersion: String,
    val arrow: ArrowType?,
    val finishedScopesCount: Int
) : Coverage

enum class ArrowType {
    INCREASE,
    DECREASE
}

@Serializable
data class BuildMethods(
    val totalMethods: MethodsInfo = MethodsInfo(),
    val newMethods: MethodsInfo = MethodsInfo(),
    val modifiedNameMethods: MethodsInfo = MethodsInfo(),
    val modifiedDescMethods: MethodsInfo = MethodsInfo(),
    val modifiedBodyMethods: MethodsInfo = MethodsInfo(),
    val allModifiedMethods: MethodsInfo = MethodsInfo(),
    val unaffectedMethods: MethodsInfo = MethodsInfo(),
    val deletedMethods: MethodsInfo = MethodsInfo(),
    val deletedCoveredMethodsCount: Int = 0
)


@Serializable
data class MethodsInfo(
    val totalCount: Int = 0,
    val coveredCount: Int = 0,
    val methods: List<JavaMethod> = emptyList()
)

@Serializable
data class PackageTree(
    val totalCount: Int,
    val packages: List<JavaPackageCoverage>
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
    val assocTestsCount: Int? = null,
    val classes: List<JavaClassCoverage>
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
    val assocTestsCount: Int? = null,
    val methods: List<JavaMethodCoverage>
)

@Serializable
data class JavaMethodCoverage(
    val id: String,
    val name: String,
    val desc: String,
    val decl: String,
    val count: Int,
    val coverage: Double = 0.0,
    val assocTestsCount: Int? = null
)

@Serializable
data class AssociatedTests(
    val id: String,
    val packageName: String?,
    val className: String?,
    val methodName: String?,
    val tests: List<TypedTest>
) {
    override fun equals(other: Any?) = other is AssociatedTests && id == other.id
    override fun hashCode() = id.hashCode()
}

@Serializable
data class TypedTest(
    val name: String,
    val type: String
)

@Serializable
data class MethodsCoveredByTest(
    val id: String,
    val testName: String,
    val testType: String,
    val newMethods: List<JavaMethod>,
    val modifiedMethods: List<JavaMethod>,
    val unaffectedMethods: List<JavaMethod>
)

@Serializable
data class MethodsCoveredByTestType(
    val testType: String,
    val testsCount: Int,
    val newMethods: List<JavaMethod>,
    val modifiedMethods: List<JavaMethod>,
    val unaffectedMethods: List<JavaMethod>
)

@Serializable
data class TestUsagesInfo(
    val id: String,
    val testName: String,
    val methodCalls: Int,
    val coverage: Double
)

@Serializable
data class TestsUsagesInfoByType(
    val testType: String,
    val coverage: Double,
    val methodsCount: Int,
    val tests: List<TestUsagesInfo>
)

@Serializable
data class ActiveSessions(
    val count: Int,
    val testTypes: Set<String>
)

@Serializable
data class ScopeSummary(
    val name: String,
    val id: String,
    val started: Long,
    val finished: Long = 0L,
    var enabled: Boolean = true,
    val active: Boolean = true,
    val coverage: ScopeCoverage = ScopeCoverage(
        ratio = 0.0,
        count = zeroCount,
        methodCount = zeroCount,
        riskCount = zeroCount,
        byTestType = emptyMap()
    )
)

val zeroCount = Count(covered = 0, total = 0)

@Serializable
data class TestTypeSummary(
    val testType: String,
    val coverage: Double = 0.0,
    val testCount: Int = 0,
    val coveredMethodsCount: Int
)

@Serializable
data class Risks(
    val newMethods: List<JavaMethod>,
    val modifiedMethods: List<JavaMethod>
)

typealias GroupedTests = Map<String, List<String>>

//TODO get rid of this unnecessary wrapping
@Serializable
data class TestsToRun(
    val testTypeToNames: GroupedTests
)

@Serializable
data class TestsToRunDto(
    val groupedTests: GroupedTests,
    val count: Int
) : (TestsToRunDto) -> TestsToRunDto {
    //TODO separate aggregation implementation from the data class
    override fun invoke(other: TestsToRunDto) = this + other
}

@Serializable
data class SummaryDto(
    val coverage: Double,
    val coverageCount: Count,
    val arrow: ArrowType?,
    val risks: Int,
    val testsToRun: TestsToRunDto
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
    val summaries: List<AgentSummaryDto>
)

@Serializable
data class Count(
    val covered: Int,
    val total: Int
)
