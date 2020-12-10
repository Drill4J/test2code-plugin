package com.epam.drill.plugins.test2code

import com.epam.drill.plugins.test2code.api.*
import com.epam.drill.plugins.test2code.coverage.*
import kotlinx.serialization.*

@Serializable
data class BuildTests(
    val tests: GroupedTests = emptyMap(),
    val assocTests: Set<AssociatedTests> = emptySet()
)

internal fun BundleCounters.testsWith(
    methods: Iterable<Method>
): GroupedTests = byTest.asSequence().takeIf { it.any() }?.run {
    val lazyMethodMap = lazy(LazyThreadSafetyMode.NONE) {
        methods.groupBy(Method::ownerClass)
    }
    val lazyPackageSet = lazy(LazyThreadSafetyMode.NONE) { methods.toPackageSet() }
    mapNotNull { (test, counter) ->
        test.takeIf {
            val packageSeq = counter.packages.asSequence()
            packageSeq.toCoveredMethods(lazyMethodMap::value, lazyPackageSet::value).any()
        }
    }.distinct().groupBy(TypedTest::type, TypedTest::name)
}.orEmpty()

internal fun GroupedTests.filter(
    predicate: (String, String) -> Boolean
): GroupedTests = asSequence().mapNotNull { (type, tests) ->
    val filtered = tests.filter { predicate(it, type) }
    filtered.takeIf { it.any() }?.let { type to it }
}.toMap()

internal fun GroupedTests.withoutCoverage(
    bundleCounters: BundleCounters
): GroupedTests = filter { name, type ->
    TypedTest(name, type) !in bundleCounters.byTest
}

internal fun GroupedTests.withCoverage(
    bundleCounters: BundleCounters
): GroupedTests = filter { name, type ->
    TypedTest(name, type) in bundleCounters.byTest
}

internal fun CoverContext.testsToRunDto(
    bundleCounters: BundleCounters = build.bundleCounters
): List<TestCoverageDto> = testsToRun.flatMap { (type, tests) ->
    tests.map { name ->
        val typedTest = TypedTest(type = type, name = name)
        TestCoverageDto(
            id = typedTest.id(),
            type = type,
            name = name,
            toRun = typedTest !in bundleCounters.byTest,
            coverage = bundleCounters.byTest[typedTest]?.toCoverDto(packageTree) ?: CoverDto(),
            stats = bundleCounters.statsByTest[typedTest] ?: TestStats(0, TestResult.PASSED)
        )
    }
}

internal fun GroupedTests.totalDuration(
    statsByTest: Map<TypedTest, TestStats>
): Long = this.flatMap { (type, tests) ->
    tests.map { name ->
        val typedTest = TypedTest(type = type, name = name)
        statsByTest[typedTest]?.duration
    }
}.filterNotNull().sum()


internal fun GroupedTests.toDto() = GroupedTestsDto(
    totalCount = totalCount(),
    byType = this
)

internal fun GroupedTests.totalCount(): Int = values.sumBy { it.count() }

//TODO remove after changes on frontend EPMDJ-5622
internal fun List<TestCoverageDto>.toUsageInfo() = groupBy { it.type }.map { (type, testCoverages) ->
    TestsUsagesInfoByType(
        testType = type,
        tests = testCoverages.map {
            TestUsagesInfo(
                id = TypedTest(it.name, type).id(),
                testName = it.name,
                methodCalls = it.coverage.methodCount.covered,
                coverage = it.coverage.percentage,
                stats = it.stats
            )
        }
    )
}
