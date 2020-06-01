package com.epam.drill.plugins.test2code

import com.epam.drill.common.*
import com.epam.drill.plugins.test2code.api.*
import com.epam.drill.plugins.test2code.common.api.*
import com.epam.drill.plugins.test2code.coverage.*

typealias ClassesBytes = Map<String, ByteArray>

internal fun ScopeSummary.calculateCoverage(
    sessions: Sequence<Session>,
    state: PluginInstanceState
): ScopeSummary = run {
    val classData = state.data as ClassData
    val bundle = sessions.flatten().bundle(state)
    val totalInstructions = classData.packageTree.totalCount
    val coverageCount = Count(bundle.count.covered, totalInstructions)
    copy(
        coverage = ScopeCoverage(
            ratio = coverageCount.percentage(),
            count = coverageCount,
            methodCount = bundle.methodCount.copy(total = classData.packageTree.totalMethodCount),
            riskCount = zeroCount,
            byTestType = sessions.coveragesByTestType(
                sessions.bundlesByTests(state),
                state
            )
        )
    )
}

internal suspend fun Sequence<Session>.calculateCoverageData(
    state: PluginInstanceState,
    buildVersion: String,
    scopeCount: Int = 0,
    otherCoverage: Count? = null
): CoverageInfoSet {
    val classData = state.classesData(buildVersion) as ClassData

    val bundlesByTests = bundlesByTests(state, buildVersion)
    val assocTestsMap = bundlesByTests.associatedTests()
    val associatedTests = assocTestsMap.getAssociatedTests()

    val totalInstructions = classData.packageTree.totalCount
    val bundleCoverage = flatten().bundle(state, buildVersion)
    val coverageCount = Count(bundleCoverage.count.covered, totalInstructions)
    val totalCoveragePercent = coverageCount.percentage()

    val scope = this as? Scope
    val coverageByType: Map<String, TestTypeSummary> = when (scope) {
        null -> coveragesByTestType(bundlesByTests, state, buildVersion)
        else -> scope.summary.coverage.byTestType
    }
    println(coverageByType)

    val parentVersion = state.buildManager[buildVersion]?.parentVersion ?: ""
    val methodCount = bundleCoverage.methodCount.copy(total = classData.packageTree.totalMethodCount)
    val coverageBlock: Coverage = when (scope) {
        null -> {
            BuildCoverage(
                ratio = totalCoveragePercent,
                count = coverageCount,
                methodCount = methodCount,
                riskCount = zeroCount,
                byTestType = coverageByType,
                diff = otherCoverage?.let {
                    (coverageCount - it).run { first percentOf second }
                } ?: coverageCount.percentage(),
                prevBuildVersion = parentVersion,
                arrow = otherCoverage?.run { arrowType(coverageCount) },
                finishedScopesCount = scopeCount
            )
        }
        else -> ScopeCoverage(
            ratio = totalCoveragePercent,
            count = coverageCount,
            methodCount = methodCount,
            riskCount = zeroCount,
            byTestType = coverageByType
        )
    }
    println(coverageBlock)

    val methodsChanges = classData.methodChanges

    val calculatedMethods = calculateBundleMethods(
        methodsChanges,
        bundleCoverage
    )
    val buildMethods = state.buildTests[state.buildId(buildVersion)]?.let {
        calculatedMethods.copy(
            deletedCoveredMethodsCount = calculatedMethods.deletedMethods.testCount(it.assocTests)
        )
    } ?: calculatedMethods

    val packageCoverage = classData.packageTree.packages.treeCoverage(bundleCoverage, assocTestsMap)

    val (coveredByTest, coveredByTestType) = bundlesByTests.coveredMethods(
        methodsChanges,
        bundlesByTestTypes(state, buildVersion)
    )

    val testsUsagesInfoByType = coverageByType.map {
        TestsUsagesInfoByType(
            it.value.testType,
            it.value.coverage,
            it.value.coveredMethodsCount,
            bundlesByTests.testUsages(
                totalInstructions,
                it.value.testType
            )
        )
    }.sortedBy { it.testType }

    return CoverageInfoSet(
        associatedTests,
        coverageBlock,
        buildMethods,
        packageCoverage,
        testsUsagesInfoByType,
        coveredByTest,
        coveredByTestType
    )
}

fun Sequence<Session>.coveragesByTestType(
    bundleMap: Map<TypedTest, BundleCounter>,
    state: PluginInstanceState,
    buildVersion: String = state.agentInfo.buildVersion
): Map<String, TestTypeSummary> = run {
    val classData = state.data as ClassData
    val totalInstructions = classData.packageTree.totalCount
    groupBy(Session::testType).mapValues { (testType, sessions) ->
        sessions.asSequence().run {
            TestTypeSummary(
                testType = testType,
                coverage = flatten().bundle(state, buildVersion).count.copy(total = totalInstructions).percentage(),
                testCount = flatMap { it.tests.asSequence() }.distinct().count(),
                coveredMethodsCount = bundleMap.coveredMethodsByTestTypeCount(testType)
            )
        }
    }
}

private fun Sequence<Session>.bundlesByTests(
    state: PluginInstanceState,
    buildVersion: String = state.agentInfo.buildVersion
): Map<TypedTest, BundleCounter> = takeIf { it.any() }?.run {
    groupBy(Session::testType).map { (testType, sessions) ->
        sessions.asSequence().flatten()
            .groupBy { TypedTest(it.testName, testType) }
            .mapValuesTo(mutableMapOf()) { it.value.asSequence().bundle(state, buildVersion) }
    }.reduce { m1, m2 ->
        m1.apply { putAll(m2) }
    }
} ?: emptyMap()

private fun Sequence<Session>.bundlesByTestTypes(
    state: PluginInstanceState,
    buildVersion: String
): Map<String, BundleCounter> = groupBy(Session::testType).mapValues {
    it.value.asSequence().flatten().bundle(state, buildVersion)
}

internal fun Sequence<ExecClassData>.bundle(
    state: PluginInstanceState,
    buildVersion: String = state.agentInfo.buildVersion
): BundleCounter = when (state.agentInfo.agentType) {
    AgentType.JAVA -> bundle(state.buildManager[buildVersion]?.classesBytes ?: emptyMap())
    else -> bundle((state.data as ClassData).packageTree)
}

private fun Map<TypedTest, BundleCounter>.associatedTests(): Map<CoverageKey, List<TypedTest>> = run {
    entries.asSequence()
        .flatMap { (test, bundle) ->
            bundle.coverageKeys().map { it to test }
        }.distinct()
        .groupBy({ it.first }) { it.second }
}

private fun Map<TypedTest, BundleCounter>.coveredMethodsByTestTypeCount(
    testType: String
): Int = entries.asSequence()
    .filter { it.key.type == testType }
    .flatMap { it.value.coverageKeys() }
    .filter(CoverageKey::isMethod)
    .distinct()
    .count()
