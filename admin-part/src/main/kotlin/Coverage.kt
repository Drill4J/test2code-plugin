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

import com.epam.drill.plugin.api.end.*
import com.epam.drill.plugins.test2code.api.*
import com.epam.drill.plugins.test2code.common.api.*
import com.epam.drill.plugins.test2code.coverage.*
import com.epam.drill.plugins.test2code.jvm.*
import com.epam.drill.plugins.test2code.util.*
import kotlinx.collections.immutable.*
import org.jacoco.core.data.*
import java.io.*

private val logger = logger {}

internal fun Sequence<Session>.calcBundleCounters(
    context: CoverContext,
    classBytes: Map<String, ByteArray>,
    cache: AtomicCache<TypedTest, BundleCounter>? = null,
): BundleCounters = run {
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
    logger.trace { "Starting to create the bundle with probesId count ${context.probeIds.size} and classes ${classBytes.size}..." }
    val bundleProcessor = BundleProcessor.bundleProcessor(context)
    val allProbes = flatten()
    BundleCounters(
        all = trackTime("bundlesAll") { bundleProcessor.bundle(allProbes) },
        testTypeOverlap = bundleProcessor.bundle(testTypeOverlap),
        overlap = allProbes.overlappingBundle(context, classBytes),
        byTestType = probesByTestType.mapValues {
            it.value.asSequence().flatten().bundle(context, classBytes)
        },
        byTest = trackTime("bundlesByTests") { bundlesByTests(bundleProcessor, cache) },
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

internal suspend fun BundleCounters.calculateCoverageData(
    context: CoverContext,
    scope: Scope? = null,
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
                duration = statsByTest.values.sumOf { it.duration }
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

    val buildMethods = trackTime("calculateBundleMethods") { context.calculateBundleMethods(bundle) }

    val packageCoverage = tree.packages.treeCoverage(bundle, assocTestsMap)

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
    bundleProc: BundleProc,
    cache: AtomicCache<TypedTest, BundleCounter>?,
): Map<TypedTest, BundleCounter> = takeIf { it.any() }?.run {
    val testsWithEmptyBundle = testsWithBundle(cache)
    groupBy(Session::testType).map { (testType, sessions) ->
        sessions.asSequence().flatten()
            .groupBy { TypedTest(it.testName, testType) }
            .filterNot { cache?.map?.containsKey(it.key) ?: false }
            .mapValuesTo(testsWithEmptyBundle) {
                bundleProc.bundle(it.value.asSequence())
            }
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
    classBytes: Map<String, ByteArray>,
): BundleCounter = context.build.probes.intersect(this).run {
    values.asSequence()
}.bundle(context, classBytes)

internal fun Sequence<ExecClassData>.bundle(
    context: CoverContext,
    classBytes: Map<String, ByteArray>,
): BundleCounter = when (context.agentType) {
    "JAVA" -> bundle(
        probeIds = context.probeIds,
        classBytes = classBytes
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

internal suspend fun Plugin.exportCoverage(buildVersion: String) = runCatching {
    val coverage = File(System.getProperty("java.io.tmpdir"))
        .resolve("jacoco.exec")
    coverage.outputStream().use { outputStream ->
        val executionDataWriter = ExecutionDataWriter(outputStream)
        val classBytes = adminData.loadClassBytes()
        val allFinishedScopes = state.scopeManager.byVersion(buildVersion, true)
        allFinishedScopes.filter { it.enabled }.flatMap { finishedScope ->
            finishedScope.data.sessions.flatMap { it.probes }
        }.writeCoverage(executionDataWriter, classBytes)
        if (buildVersion == buildVersion) {
            activeScope.flatMap {
                it.probes
            }.writeCoverage(executionDataWriter, classBytes)
        }
    }
    ActionResult(StatusCodes.OK, coverage)
}.getOrElse {
    logger.error(it) { "Can't get coverage. Reason:" }
    ActionResult(StatusCodes.BAD_REQUEST, "Can't get coverage.")
}

private fun Sequence<ExecClassData>.writeCoverage(
    executionDataWriter: ExecutionDataWriter,
    classBytes: Map<String, ByteArray>,
) = forEach { execClassData ->
    executionDataWriter.visitClassExecution(
        ExecutionData(
            classBytes[execClassData.className]?.crc64() ?: execClassData.id(),
            execClassData.className,
            execClassData.probes.toBooleanArray()
        )
    )
}
