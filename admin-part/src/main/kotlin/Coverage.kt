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

internal fun Iterable<Session>.calcBundleCounters(
    context: CoverContext,
    cache: AtomicCache<TypedTest, BundleCounter>? = null
) = run {
    logger.trace {
        "CalcBundleCounters for ${context.build.version} sessions(size=${this.toList().size}, ids=${
            this.toList().map { it.id + " " }
        })..."
    }
    val probesByTestType = groupBy(Session::testType)
    val testTypeOverlap = if (probesByTestType.size > 1) {
        probesByTestType.values.run {
            val initial: PersistentMap<Long, ExecClassData> = first().flatten().merge()
            drop(1).fold(initial) { intersection, sessions ->
                intersection.intersect(sessions.flatten())
            }
        }.values
    } else emptyList()

    logger.trace { "Starting to create the bundle with probesId count ${context.probeIds.size} and classes ${context.classBytes.size}..." }
    val bundleProcessor = BundleProcessor.bundleProcessor(context)
    val execClassData = flatten()
    BundleCounters(
        all = trackTime("bundle all") { bundleProcessor.bundle(execClassData) },
        testTypeOverlap = bundleProcessor.bundle(testTypeOverlap),
        overlap = bundleProcessor.bundle(context.build.probes.intersect(execClassData).values),
        byTestType = probesByTestType.mapValues { bundleProcessor.bundle(it.value.flatten()) },
        byTest = trackTime("bundle by test") {
            bundlesByTests(
                bundleProcessor,
                cache,
                context
            )
        },
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
    cache: AtomicCache<TypedTest, MethodsCoveredByTest>? = null
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

    val finalizedTests = (scope as? ActiveScope)?.flatMap { it.testStats.keys } ?: emptyList()

    val coveredByTest =
        trackTime("coveredByTest") { bundlesByTests.methodsCoveredByTest(context, cache, finalizedTests) }

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

private fun Iterable<Session>.bundlesByTests(
    bundleProc: BundleProc,
    cache: AtomicCache<TypedTest, BundleCounter>?,
    context: CoverContext
): Map<TypedTest, BundleCounter> = takeIf { it.any() }?.run {
    val toMap: Map<ExecClassData, ClassCounter?> = map { session ->
        val calculed = session.calculated
        session.map {
            it to calculed[it]
        }

    }.flatten().toMap()  //fixme
    val fullAnalyzedTree = context.analyzedClasses.groupBy { it.packageName }
        .map {
            PackageCoverage(it.key).apply {
                totalInstruction = it.value.map { it.totalInstruction }.sum()
                totalClasses = it.value.size
                totalMethods = it.value.map { it.methods.values }.flatten().size
                classes = it.value
            }
        }.associateBy { it.packageName }
        .mapValues {
            it.value to it.value.classes.associateBy { it.jvmClassName }
        }
    val testsWithEmptyBundle = testsWithBundle(cache)
    groupBy(Session::testType).map { (testType, sessions) ->

        sessions.asSequence().flatten()
            .groupBy { TypedTest(it.testName, testType) }
            .filterNot { cache?.map?.containsKey(it.key) ?: false }
            .mapValuesTo(testsWithEmptyBundle) {
                if(context.agentType == "JAVA") //fixme
                    it.value.mapNotNull { toMap[it] }.toBundle(fullAnalyzedTree)
                else  //fixme
                    it.value.asSequence().bundle(context)
//                bundleProc.fastBundle(precalculated.filterKeys {w-> x.value.any { it == w } })
            }
    }.reduce { m1, m2 ->
        m1.apply { putAll(m2) }
    }.let { mutableMap ->
        val finalizedTests = flatMap { it.testStats.keys }
        val testsAddToCache = mutableMap.filterKeys { finalizedTests.contains(it) }
        mutableMap + (cache?.putAll(testsAddToCache) ?: emptyMap())
    }
} ?: emptyMap()

private fun Iterable<Session>.testsWithBundle(
    cache: AtomicCache<TypedTest, BundleCounter>?,
    bundle: BundleCounter = BundleCounter("")
): MutableMap<TypedTest, BundleCounter> = flatMap { session ->
    session.tests.asSequence().filterNot { cache?.map?.containsKey(it) ?: false }
}.associateWithTo(mutableMapOf()) {
    bundle
}


internal fun Sequence<ExecClassData>.bundle(
    context: CoverContext
): BundleCounter = when (context.agentType) {
    "JAVA" -> bundle(
        probeIds = context.probeIds,
        classBytes = context.classBytes
    )
    else -> bundle(context.packageTree)
}

internal fun Map<TypedTest, BundleCounter>.associatedTests(
    onlyPackages: Boolean = true
): Map<CoverageKey, List<TypedTest>> = run {
    flatMapTo(mutableSetOf()) { (test, bundle) ->
        bundle.coverageKeys(onlyPackages).map { it to test }
    }.groupBy({ it.first }) { it.second }
}

