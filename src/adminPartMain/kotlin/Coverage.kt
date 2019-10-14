package com.epam.drill.plugins.coverage

import org.jacoco.core.analysis.*
import org.jacoco.core.data.*
import kotlin.math.*

typealias ClassesBytes = Map<String, ByteArray>

fun ClassesBytes.coverageBundle(data: Sequence<ExecClassData>): IBundleCoverage {
    val coverageBuilder = CoverageBuilder()
    val dataStore = ExecutionDataStore().with(data)
    val analyzer = Analyzer(dataStore, coverageBuilder)
    dataStore.contents.forEach {
        analyzer.analyzeClass(this[it.name], it.name)
    }
    return coverageBuilder.getBundle("")
}

fun ClassesBytes.coverage(data: Sequence<FinishedSession>, totalInstructions: Int) =
    coverageBundle(data.flatten()).coverage(totalInstructions)

fun ClassesBytes.coveragesByTestType(
    data: Sequence<FinishedSession>,
    totalInstructions: Int
): Map<String, TestTypeSummary> {
    return data.groupBy { it.testType }.mapValues { (testType, finishedSessions) ->
        TestTypeSummary(
            testType = testType,
            coverage = coverage(finishedSessions.asSequence(), totalInstructions),
            testCount = finishedSessions.flatMap { it.testNames }.distinct().count(),
            coveredMethodsCount = coveredMethodsByTestTypeCount(data, testType) ?: 0
        )
    }
}

fun ClassesData.arrowType(totalCoveragePercent: Double): ArrowType? {
    val diff = totalCoveragePercent - prevBuildCoverage
    return when {
        abs(diff) < 1E-7 -> null
        diff > 0.0 -> ArrowType.INCREASE
        else -> ArrowType.DECREASE
    }
}

fun ClassesBytes.coveredMethodsByTestTypeCount(data: Sequence<FinishedSession>, testType: String) = bundlesByTests(data)
    .flatMap { (test, bundle) -> bundle.collectAssocTestPairs(test) }
    .filter { !it.first.methodName.isNullOrEmpty() && it.second.type == testType }
    .groupBy { it.second.type }[testType]?.count()

fun ClassesBytes.associatedTests(data: Sequence<FinishedSession>) = bundlesByTests(data)
    .flatMap { (test, bundle) ->
        bundle.collectAssocTestPairs(test)
    }.groupBy({ it.first }) { it.second } //group by test names
    .mapValues { it.value.distinct() }

fun ClassesBytes.bundlesByTests(data: Sequence<FinishedSession>): Map<TypedTest, IBundleCoverage> {
    return data.flatMap { it.probes.asSequence() }
        .groupBy({ it.key }) { it.value }
        .mapValues { it.value.asSequence().flatten() }
        .mapValues { coverageBundle(it.value) }
}