package com.epam.drill.plugins.test2code

import com.epam.drill.common.*
import com.epam.drill.plugins.test2code.api.*
import com.epam.drill.plugins.test2code.common.api.*
import com.epam.drill.plugins.test2code.coverage.*
import mu.*

typealias ClassesBytes = Map<String, ByteArray>

private val logger = KotlinLogging.logger {}

internal fun ScopeSummary.calculateCoverage(
    sessions: Sequence<Session>,
    context: CoverContext
): ScopeSummary = run {
    val bundle = sessions.flatten().bundle(context)
    val totalInstructions = context.packageTree.totalCount
    val coverageCount = Count(bundle.count.covered, totalInstructions)
    copy(
        coverage = ScopeCoverage(
            ratio = coverageCount.percentage(),
            count = coverageCount,
            methodCount = bundle.methodCount.copy(total = context.packageTree.totalMethodCount),
            riskCount = zeroCount,
            byTestType = sessions.coveragesByTestType(
                sessions.bundlesByTests(context),
                context
            )
        )
    )
}

internal fun Sequence<Session>.calculateCoverageData(
    context: CoverContext,
    scopeCount: Int = 0,
    otherCoverage: Count? = null
): CoverageInfoSet {
    val bundlesByTests = bundlesByTests(context)
    val assocTestsMap = bundlesByTests.associatedTests()
    val associatedTests = assocTestsMap.getAssociatedTests()

    val totalInstructions = context.packageTree.totalCount
    val bundleCoverage = flatten().bundle(context)
    val coverageCount = Count(bundleCoverage.count.covered, totalInstructions)
    val totalCoveragePercent = coverageCount.percentage()

    val scope = this as? Scope
    val coverageByType: Map<String, TestTypeSummary> = when (scope) {
        null -> coveragesByTestType(bundlesByTests, context)
        else -> scope.summary.coverage.byTestType
    }
    logger.info { coverageByType }

    val methodCount = bundleCoverage.methodCount.copy(total = context.packageTree.totalMethodCount)
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
                prevBuildVersion = context.parentVersion,
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
    logger.info { coverageBlock }

    val buildMethods = context.calculateBundleMethods(bundleCoverage).run {
        context.tests?.let {
            copy(deletedCoveredMethodsCount = deletedMethods.testCount(it.assocTests))
        } ?: this
    }

    val packageCoverage = context.packageTree.packages.treeCoverage(bundleCoverage, assocTestsMap)

    val (coveredByTest, coveredByTestType) = bundlesByTests.coveredMethods(
        context,
        bundlesByTestTypes(context)
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
    context: CoverContext
): Map<String, TestTypeSummary> = run {
    val totalInstructions = context.packageTree.totalCount
    groupBy(Session::testType).mapValues { (testType, sessions) ->
        sessions.asSequence().run {
            TestTypeSummary(
                testType = testType,
                coverage = flatten().bundle(context).count.copy(total = totalInstructions).percentage(),
                testCount = flatMap { it.tests.asSequence() }.distinct().count(),
                coveredMethodsCount = bundleMap.coveredMethodsByTestTypeCount(testType)
            )
        }
    }
}

private fun Sequence<Session>.bundlesByTests(
    context: CoverContext
): Map<TypedTest, BundleCounter> = takeIf { it.any() }?.run {
    groupBy(Session::testType).map { (testType, sessions) ->
        sessions.asSequence().flatten()
            .groupBy { TypedTest(it.testName, testType) }
            .mapValuesTo(mutableMapOf()) { it.value.asSequence().bundle(context) }
    }.reduce { m1, m2 ->
        m1.apply { putAll(m2) }
    }
} ?: emptyMap()

private fun Sequence<Session>.bundlesByTestTypes(
    context: CoverContext
): Map<String, BundleCounter> = groupBy(Session::testType).mapValues {
    it.value.asSequence().flatten().bundle(context)
}

internal fun Sequence<ExecClassData>.bundle(
    context: CoverContext
): BundleCounter = when (context.agentType) {
    AgentType.JAVA -> bundle(
        probeIds = context.probeIds,
        classBytes = context.classBytes
    )
    else -> bundle(context.packageTree)
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
