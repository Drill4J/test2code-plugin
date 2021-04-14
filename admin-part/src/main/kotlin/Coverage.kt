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
import com.epam.drill.plugins.test2code.coverage.*
import com.epam.drill.plugins.test2code.jvm.*
import com.epam.drill.plugins.test2code.util.*
import kotlinx.collections.immutable.*

private val logger = logger {}

internal fun Sequence<Session>.calcBundleCounters(
    context: CoverContext,
    cache: AtomicCache<TypedTest, BundleCounter>? = null,
) = run {
    logger.trace {
        "CalcBundleCounters for ${context.build.version} sessions(size=${this.toList().size}, ids=${
            this.toList().map { it.id + " " }
        })..."
    }
    val probesByTestType = groupBy(Session::testType)
    val testTypeOverlap: Sequence<ExecClassData> = if (probesByTestType.size > 1) {
        probesByTestType.values.asSequence().run {
            val initial: PersistentMap<Long, ExecClassData> = first().asSequence().flatten().merge()
            drop(1).fold(initial) { intersection, sessions ->
                intersection.intersect(sessions.asSequence().flatten())
            }
        }.values.asSequence()
    } else emptySequence()
    logger.trace { "Starting to create the bundle with probesId count ${context.probeIds.size} and classes ${context.classBytes.size}..." }
    BundleCounters(
        all = flatten().bundle(context),
        testTypeOverlap = testTypeOverlap.bundle(context),
        overlap = flatten().overlappingBundle(context),
        byTestType = probesByTestType.mapValues {
            it.value.asSequence().flatten().bundle(context)
        },
        byTest = trackTime("bundlesByTests") { bundlesByTests(context, cache) },
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
    scope: Scope? = null,
    cache: AtomicCache<TypedTest, MethodsCoveredByTest>? = null,
): CoverageInfoSet {
    val bundle = all
    val bundlesByTests = byTest

    val assocTestsMap = trackTime("assocTestsMap") { bundlesByTests.associatedTests() }
    val associatedTests = trackTime("associatedTests") { assocTestsMap.getAssociatedTests() }

    val tree = context.packageTree
    val coverageCount = bundle.count.copy(total = tree.totalCount)
    val totalCoveragePercent = coverageCount.percentage()

    val coverageByTests = trackTime("coverageByTests") {
        CoverageByTests(
            all = TestSummary(
                coverage = bundle.toCoverDto(tree),
                testCount = bundlesByTests.keys.count(),
                duration = statsByTest.values.map { it.duration }.sum()
            ),
            byType = byTestType.coveragesByTestType(byTest, context, statsByTest)
        )
    }
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

    val finalizedTests = (scope as? ActiveScope)?.flatMap { it.testStats.keys } ?: emptySequence()
    println("METHODS BY TESTS")
    println("Size: ${bundlesByTests.size}")
    val coveredByTest = trackTime("coveredByTest") {
        bundlesByTests.methodsCoveredByTest(context, cache, finalizedTests)
    }

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
    statsByTest: Map<TypedTest, TestStats>,
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
    context: CoverContext,
    cache: AtomicCache<TypedTest, BundleCounter>?,
): Map<TypedTest, BundleCounter> = takeIf { it.any() }?.run {
    val testsWithEmptyBundle = testsWithBundle(cache)
    groupBy(Session::testType).map { (testType, sessions) ->
        sessions.asSequence().flatten()
            .groupBy { TypedTest(it.testName, testType) }
            .filterNot { cache?.map?.containsKey(it.key) ?: false }
            .mapValuesTo(testsWithEmptyBundle) { it.value.asSequence().bundle(context) }
    }.reduce { m1, m2 ->
        m1.apply { putAll(m2) }
    }.let { mutableMap ->
        val finalizedTests = flatMap { it.testStats.keys }
        val testsAddToCache = mutableMap.filterKeys { finalizedTests.contains(it) }
        mutableMap + (cache?.putAll(testsAddToCache) ?: emptyMap())
    }
} ?: emptyMap()

private fun Sequence<Session>.testsWithBundle(
    cache: AtomicCache<TypedTest, BundleCounter>?,
    bundle: BundleCounter = BundleCounter(""),
): MutableMap<TypedTest, BundleCounter> = flatMap { session ->
    session.tests.asSequence().filterNot { cache?.map?.containsKey(it) ?: false }
}.associateWithTo(mutableMapOf()) {
    bundle
}


internal fun Sequence<ExecClassData>.overlappingBundle(
    context: CoverContext,
): BundleCounter = context.build.probes.intersect(this).run {
    values.asSequence()
}.bundle(context)

internal fun Sequence<ExecClassData>.bundle(
    context: CoverContext,
): BundleCounter = when (context.agentType) {
    "JAVA" -> bundle(
        probeIds = context.probeIds,
        classBytes = context.classBytes
    )
    else -> bundle(context.packageTree)
}

internal fun Map<TypedTest, BundleCounter>.associatedTests(
    onlyPackages: Boolean = true,
): Map<CoverageKey, List<TypedTest>> = entries.asSequence()
    .flatMap { (test, bundle) ->
        bundle.coverageKeys(onlyPackages).map { it to test }
    }.distinct()
    .groupBy({ it.first }) { it.second }
