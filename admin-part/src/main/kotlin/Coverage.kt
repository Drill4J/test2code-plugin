package com.epam.drill.plugins.test2code

import com.epam.drill.common.*
import com.epam.drill.plugins.test2code.api.*
import com.epam.drill.plugins.test2code.common.api.*
import com.epam.drill.plugins.test2code.coverage.*
import com.epam.drill.plugins.test2code.jvm.*
import mu.*

private val logger = KotlinLogging.logger {}

internal fun Sequence<Session>.calcBundleCounters(context: CoverContext) = BundleCounters(
    all = flatten().bundle(context),
    byTestType = bundlesByTestTypes(context),
    byTest = bundlesByTests(context)
)

internal fun ScopeSummary.calculateCoverage(
    sessions: Sequence<Session>,
    context: CoverContext
): ScopeSummary = run {
    val probes = sessions.flatten()
    val bundles = sessions.calcBundleCounters(context)
    val bundle = bundles.all
    val overlappingBundle = probes.overlappingBundle(context)
    val coverageCount = bundle.count.copy(total = context.packageTree.totalCount)
    val coveragePercent = coverageCount.percentage()
    copy(
        coverage = ScopeCoverage(
            ratio = coveragePercent,
            percentage = coveragePercent,
            count = coverageCount,
            overlap = overlappingBundle.toCoverDto(context.packageTree),
            methodCount = bundle.methodCount.copy(total = context.packageTree.totalMethodCount),
            classCount = bundle.classCount.copy(total = context.packageTree.totalClassCount),
            packageCount = bundle.packageCount.copy(total = context.packageTree.packages.count()),
            riskCount = zeroCount,
            risks = RiskSummaryDto(),
            byTestType = bundles.byTestType.coveragesByTestType(bundles.byTest, context)
        )
    )
}

internal fun BundleCounters.calculateCoverageData(
    context: CoverContext,
    scope: Scope? = null

): CoverageInfoSet {
    val bundle = all
    val bundlesByTestTypes = byTestType
    val bundlesByTests = byTest

    val assocTestsMap = bundlesByTests.associatedTests()
    val associatedTests = assocTestsMap.getAssociatedTests()

    val totalCount = context.packageTree.totalCount
    val coverageCount = bundle.count.copy(total = totalCount)
    val totalCoveragePercent = coverageCount.percentage()

    val coverageByTests = CoverageByTests(
        all = TestSummary(
            coverage = bundle.toCoverDto(context.packageTree),
            testCount = bundlesByTests.keys.count()
        ),
        byType = scope?.run { summary.coverage.byTestType } ?: byTestType.coveragesByTestType(byTest, context)
    )
    logger.info { coverageByTests.byType }

    val methodCount = bundle.methodCount.copy(total = context.packageTree.totalMethodCount)
    val classCount = bundle.classCount.copy(total = context.packageTree.totalClassCount)
    val packageCount = bundle.packageCount.copy(total = context.packageTree.packages.count())
    val coverageBlock: Coverage = when (scope) {
        null -> {
            BuildCoverage(
                ratio = totalCoveragePercent,
                percentage = totalCoveragePercent,
                count = coverageCount,
                methodCount = methodCount,
                classCount = classCount,
                packageCount = packageCount,
                byTestType = coverageByTests.byType
            )
        }
        is FinishedScope -> scope.summary.coverage
        else -> ScopeCoverage(
            ratio = totalCoveragePercent,
            percentage = totalCoveragePercent,
            count = coverageCount,
            methodCount = methodCount,
            classCount = classCount,
            packageCount = packageCount,
            riskCount = zeroCount,
            byTestType = coverageByTests.byType
        )
    }
    logger.info { coverageBlock }

    val buildMethods = context.calculateBundleMethods(bundle).run {
        context.build?.let {
            copy(deletedCoveredMethodsCount = deletedMethods.testCount(it.tests.assocTests))
        } ?: this
    }

    val packageCoverage = context.packageTree.packages.treeCoverage(bundle, assocTestsMap)

    val coveredByTest = bundlesByTests.methodsCoveredByTest(context)
    val coveredByTestType = bundlesByTestTypes.methodsCoveredByType(context, bundlesByTests)

    val testsUsagesInfoByType = coverageByTests.byType.map { (testType, summary) ->
        TestsUsagesInfoByType(
            testType = testType,
            coverage = summary.coverage.percentage,
            methodsCount = summary.coverage.methodCount.covered,
            tests = bundlesByTests.testUsages(totalCount, testType)
        )
    }.sortedBy { it.testType }

    return CoverageInfoSet(
        associatedTests,
        coverageBlock,
        buildMethods,
        packageCoverage,
        testsUsagesInfoByType,
        coverageByTests,
        coveredByTest,
        coveredByTestType
    )
}

internal fun Map<String, BundleCounter>.coveragesByTestType(
    bundleMap: Map<TypedTest, BundleCounter>,
    context: CoverContext
): List<TestTypeSummary> = map { (testType, bundle) ->
    TestTypeSummary(
        type = testType,
        summary = TestSummary(
            coverage = bundle.toCoverDto(context.packageTree),
            testCount = bundleMap.keys.filter { it.type == testType }.distinct().count()
        )
    )
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

@Suppress("SimpleRedundantLet")
internal fun Sequence<ExecClassData>.overlappingBundle(
    context: CoverContext
): BundleCounter = (context.build?.probes?.let {
    it.intersect(this).values.asSequence()
} ?: emptySequence()).bundle(context)

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
