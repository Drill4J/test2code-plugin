package com.epam.drill.plugins.test2code

import com.epam.kodux.*
import kotlinx.serialization.*

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
    val coverage: Double
    val coverageByType: Map<String, TestTypeSummary>
}

@Serializable
data class ScopeCoverage(
    override val coverage: Double,
    override val coverageByType: Map<String, TestTypeSummary>
) : Coverage


@Serializable
data class BuildCoverage(
    override val coverage: Double,
    val diff: Double,
    val previousBuildInfo: Pair<String, String>,
    override val coverageByType: Map<String, TestTypeSummary>,
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
data class JavaPackageCoverage(
    val id: String,
    val name: String,
    val coverage: Double,
    val totalClassesCount: Int,
    val coveredClassesCount: Int,
    val totalMethodsCount: Int,
    val coveredMethodsCount: Int,
    val classes: List<JavaClassCoverage>,
    val assocTestsCount: Int?
)

@Serializable
data class JavaClassCoverage(
    val id: String,
    val name: String,
    val path: String,
    val coverage: Double,
    val totalMethodsCount: Int,
    val coveredMethodsCount: Int,
    val methods: List<JavaMethodCoverage>,
    val assocTestsCount: Int?
)

@Serializable
data class JavaMethodCoverage(
    val id: String,
    val name: String,
    val desc: String,
    val decl: String,
    val coverage: Double,
    val assocTestsCount: Int?
)

@Serializable
data class SimpleJavaMethodCoverage(
    val name: String,
    val desc: String,
    val ownerClass: String,
    val coverage: Double
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
    val coverage: Double = 0.0,
    var enabled: Boolean = true,
    val active: Boolean = true,
    val coveragesByType: Map<String, TestTypeSummary> = emptyMap()
)

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
    val groupedTests : GroupedTests,
    val count: Int
) : (Any) -> Any {
    //TODO separate aggregation implementation from the data class
    override fun invoke(other: Any): Any = this + other
}

@Serializable
data class LastBuildCoverage(
    @Id val id: String,
    val coverage: Double,
    val arrow: String?,
    val risks: Int,
    val testsToRun: TestsToRunDto
)

@Serializable
data class SummaryDto(
    val coverage: Double,
    val arrow: ArrowType?,
    val risks: Int,
    val testsToRun: TestsToRunDto,
    val _aggCoverages: List<Double>
) : (Any) -> Any {
    //TODO separate aggregation implementation from the data class
    override fun invoke(other: Any): Any = this + other
}
