package com.epam.drill.plugins.test2code

import com.epam.drill.common.*
import com.epam.drill.plugins.test2code.api.*
import com.epam.drill.plugins.test2code.common.api.*
import com.epam.drill.plugins.test2code.coverage.*
import com.epam.drill.plugins.test2code.jvm.*
import kotlinx.collections.immutable.*

private val logger = logger {}

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
    val bundlesByTests = byTest

    val assocTestsMap = bundlesByTests.associatedTests()
    val associatedTests = assocTestsMap.getAssociatedTests()

    val tree = context.packageTree
    val coverageCount = bundle.count.copy(total = tree.totalCount)
    val totalCoveragePercent = coverageCount.percentage()

    val coverageByTests = CoverageByTests(
        all = TestSummary(
            coverage = bundle.toCoverDto(tree),
            testCount = bundlesByTests.keys.count(),
            duration = statsByTest.values.map { it.duration }.sum()
        ),
        byType = byTestType.coveragesByTestType(byTest, context, statsByTest)
    )
    logger.info { coverageByTests.byType }

    val methodCount = bundle.methodCount.copy(total = tree.totalMethodCount)
    val classCount = bundle.classCount.copy(total = tree.totalClassCount)
    val packageCount = bundle.packageCount.copy(total = tree.packages.count())
    val coverageBlock: Coverage = when (scope) {
        null -> {
            BuildCoverage(
                percentage = totalCoveragePercent,
                count = coverageCount,
                methodCount = methodCount,
                classCount = classCount,
                packageCount = packageCount,
                testTypeOverlap = testTypeOverlap.toCoverDto(tree),
                byTestType = coverageByTests.byType
            )
        }
        is FinishedScope -> scope.summary.coverage
        else -> ScopeCoverage(
            percentage = totalCoveragePercent,
            count = coverageCount,
            overlap = overlap.toCoverDto(tree),
            methodCount = methodCount,
            classCount = classCount,
            packageCount = packageCount,
            testTypeOverlap = testTypeOverlap.toCoverDto(tree),
            byTestType = coverageByTests.byType
        )
    }
    logger.info { coverageBlock }

    val buildMethods = context.calculateBundleMethods(bundle)

    val packageCoverage = tree.packages.treeCoverage(bundle, assocTestsMap)

    val coveredByTest = bundlesByTests.methodsCoveredByTest(context)

    val tests = bundlesByTests.map { (typedTest, bundle) ->
        TestCoverageDto(
            id = typedTest.id(),
            type = typedTest.type,
            name = typedTest.name,
            coverage = bundle.toCoverDto(tree),
            stats = statsByTest[typedTest] ?: TestStats(0, TestResult.PASSED)
        )
    }.sortedBy { it.type }

    return CoverageInfoSet(
        associatedTests,
        coverageBlock,
        buildMethods,
        packageCoverage,
        tests,
        coverageByTests,
        coveredByTest
    )
}

internal fun Map<String, BundleCounter>.coveragesByTestType(
    bundleMap: Map<TypedTest, BundleCounter>,
    context: CoverContext,
    statsByTest: Map<TypedTest, TestStats>
): List<TestTypeSummary> = map { (testType, bundle) ->
    TestTypeSummary(
        type = testType,
        summary = TestSummary(
            coverage = bundle.toCoverDto(context.packageTree),
            testCount = bundleMap.keys.filter { it.type == testType }.distinct().count(),
            duration = statsByTest.filter { it.key.type == testType }.map { it.value.duration }.sum()
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

internal fun Sequence<ExecClassData>.overlappingBundle(
    context: CoverContext
): BundleCounter = context.build.probes.intersect(this).run {
    values.asSequence()
}.bundle(context)

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
