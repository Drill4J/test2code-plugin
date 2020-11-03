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
): GroupedTests = byTest.associatedTests().filter { (coverageKey, _) ->
    methods.any { it.ownerClass == coverageKey.className && it.name == coverageKey.methodName }
}.values.flatten().distinct().groupBy({ it.type }, { it.name })


internal fun GroupedTests.filter(
    predicate: (String, String) -> Boolean
): GroupedTests = asSequence().mapNotNull { (type, tests) ->
    val filtered = tests.filter { predicate(it, type) }
    filtered.takeIf { it.any() }?.let { type to it }
}.toMap()

internal fun GroupedTests.withoutCoverage(bundleCounters: BundleCounters) = filter { name, type ->
    TypedTest(name, type) !in bundleCounters.byTest
}

internal fun CoverContext.testsToRunDto(
    bundleCounters: BundleCounters = build.bundleCounters
): List<TestCoverageDto> = testsToRun.flatMap { (type, list) ->
    list.map { TypedTest(it, type) }
}.distinct().map {
    val coverMap = bundleCounters.byTest.mapValues { (_, bundle) ->
        bundle.toCoverDto(packageTree)
    }
    TestCoverageDto(
        id = it.id(),
        type = it.type,
        name = it.name,
        toRun = it !in coverMap,
        coverage = coverMap[it] ?: CoverDto(),
        stats = bundleCounters.statsByTest[it] ?: TestStats(0, TestResult.PASSED)
    )
}
