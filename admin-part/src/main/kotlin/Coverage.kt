package com.epam.drill.plugins.test2code

import com.epam.drill.common.*
import com.epam.drill.plugins.test2code.api.*
import com.epam.drill.plugins.test2code.common.api.*
import com.epam.drill.plugins.test2code.coverage.*
import com.epam.drill.plugins.test2code.jvm.*
import kotlinx.collections.immutable.*
import mu.*

private val logger = KotlinLogging.logger {}

internal fun Sequence<Session>.calcBundleCounters(
    context: CoverContext
) = run {
    val probesByTestType = groupBy(Session::testType)
    val testTypeOverlap: Sequence<ExecClassData> = if (probesByTestType.size > 1) {
        probesByTestType.values.asSequence().run {
            val initial: PersistentMap<Long, ExecClassData> = first().asSequence().flatten().merge()
            drop(1).fold(initial) { intersection, sessions ->
                intersection.intersect(sessions.asSequence().flatten())
            }
        }.values.asSequence()
    } else emptySequence()
    BundleCounters(
        all = flatten().bundle(context),
        testTypeOverlap = testTypeOverlap.bundle(context),
        overlap = flatten().overlappingBundle(context),
        byTestType = probesByTestType.mapValues {
            it.value.asSequence().flatten().bundle(context)
        },
        byTest = bundlesByTests(context),
        statsByTest = fold(mutableMapOf()) { map, session ->
            session.testStats.forEach { (test, stats) ->
                map[test] = map[test]?.run {
                    copy(duration = duration + stats.duration, result = stats.result)
                } ?: stats
            }
            map
        }
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
        byType = byTestType.coveragesByTestType(byTest, context)
    )
    logger.info { coverageByTests.byType }

    val methodCount = bundle.methodCount.copy(total = context.packageTree.totalMethodCount)
    val classCount = bundle.classCount.copy(total = context.packageTree.totalClassCount)
    val packageCount = bundle.packageCount.copy(total = context.packageTree.packages.count())
    val coverageBlock: Coverage = when (scope) {
        null -> {
            BuildCoverage(
                percentage = totalCoveragePercent,
                count = coverageCount,
                methodCount = methodCount,
                classCount = classCount,
                packageCount = packageCount,
                testTypeOverlap = testTypeOverlap.toCoverDto(context.packageTree),
                byTestType = coverageByTests.byType
            )
        }
        is FinishedScope -> scope.summary.coverage
        else -> ScopeCoverage(
            percentage = totalCoveragePercent,
            count = coverageCount,
            overlap = overlap.toCoverDto(context.packageTree),
            methodCount = methodCount,
            classCount = classCount,
            packageCount = packageCount,
            testTypeOverlap = testTypeOverlap.toCoverDto(context.packageTree),
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
            tests = testUsages(totalCount, testType)
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
    val map = flatMap { it.tests.asSequence() }.associateWithTo(mutableMapOf()) {
        BundleCounter("")
    }
    groupBy(Session::testType).map { (testType, sessions) ->
        sessions.asSequence().flatten()
            .groupBy { TypedTest(it.testName, testType) }
            .mapValuesTo(map) { it.value.asSequence().bundle(context) }
    }.reduce { m1, m2 ->
        m1.apply { putAll(m2) }
    }
} ?: emptyMap()

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
