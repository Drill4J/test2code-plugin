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
import com.epam.drill.plugins.test2code.coverage.*
import kotlinx.serialization.*

@Serializable
data class BuildTests(
    val tests: GroupedTests = emptyMap(),
) : JvmSerializable

internal fun BundleCounters.testsWith(
    methods: Iterable<Method>,
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
    }.distinct().groupBy(TypedTest::type) {
        TestData(it.name, detailsByTest[it]?.metadata ?: TestMetadata.emptyMetadata)
    }
}.orEmpty()

internal fun GroupedTests.filter(
    predicate: (String, String) -> Boolean,
): GroupedTests = asSequence().mapNotNull { (type, testData) ->
    val filtered = testData.filter { predicate(it.name, type) }
    filtered.takeIf { it.any() }?.let { type to it }
}.toMap()

internal fun GroupedTests.withoutCoverage(
    bundleCounters: BundleCounters,
): GroupedTests = filter { name, type ->
    TypedTest(name, type) !in bundleCounters.byTest
}

internal fun GroupedTests.withCoverage(
    bundleCounters: BundleCounters,
): GroupedTests = filter { name, type ->
    TypedTest(name, type) in bundleCounters.byTest
}

internal fun CoverContext.testsToRunDto(
    bundleCounters: BundleCounters = build.bundleCounters,
): List<TestCoverageDto> = testsToRun.flatMap { (type, testData) ->
    testData.map { test ->
        val typedTest = TypedTest(type = type, name = test.name)
        TestCoverageDto(
            id = typedTest.id(),
            type = type,
            name = test.name,
            toRun = typedTest !in bundleCounters.byTest,
            coverage = bundleCounters.byTest[typedTest]?.toCoverDto(packageTree) ?: CoverDto(),
            details = bundleCounters.detailsByTest[typedTest] ?: TestDetails(0, TestResult.PASSED, test.metadata)
        )
    }
}

internal fun GroupedTests.totalDuration(
    detailsByTest: Map<TypedTest, TestDetails>,
): Long = this.flatMap { (type, testData) ->
    testData.map { test ->
        val typedTest = TypedTest(type = type, name = test.name)
        detailsByTest[typedTest]?.duration
    }
}.filterNotNull().sum()


internal fun GroupedTests.toDto() = GroupedTestsDto(
    totalCount = totalCount(),
    byType = this
)

internal fun GroupedTests.totalCount(): Int = values.sumBy { it.count() }
