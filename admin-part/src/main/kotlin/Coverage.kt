package com.epam.drill.plugins.test2code

import com.epam.drill.common.*
import com.epam.drill.plugins.test2code.api.*
import com.epam.drill.plugins.test2code.common.api.*
import org.jacoco.core.analysis.*
import org.jacoco.core.data.*
import kotlin.math.*

typealias ClassesBytes = Map<String, ByteArray>

internal fun ScopeSummary.calculateCoverage(
    sessions: Sequence<Session>,
    state: PluginInstanceState
): ScopeSummary = run {
    val classData = state.data as ClassData
    state.buildInfo?.classesBytes?.let { classesBytes ->
        val totalInstructions = classData.packageTree.totalCount
        val bundle = sessions.flatten().bundle(classesBytes)
        val coverageCount = Count(bundle.instructionCounter.coveredCount, totalInstructions)
        copy(
            coverage = ScopeCoverage(
                ratio = coverageCount.percentage(),
                count = coverageCount,
                methodCount = bundle.methodCounter.toCount(classData.packageTree.totalMethodCount),
                riskCount = zeroCount,
                byTestType = sessions.coveragesByTestType(
                    sessions.bundlesByTests(classesBytes),
                    classesBytes,
                    totalInstructions
                )
            )
        )
    } ?: this
}

internal suspend fun Sequence<Session>.calculateCoverageData(
    state: PluginInstanceState,
    buildVersion: String,
    scopeCount: Int = 0,
    otherCoverage: Count? = null
): CoverageInfoSet {
    val buildInfo = state.buildManager[buildVersion]
    val classesBytes: ClassesBytes = buildInfo?.classesBytes ?: emptyMap()
    val classData = state.classesData(buildVersion) as ClassData

    val bundlesByTests = bundlesByTests(classesBytes)
    val assocTestsMap = bundlesByTests.associatedTests()
    val associatedTests = assocTestsMap.getAssociatedTests()

    val totalInstructions = classData.packageTree.totalCount
    val bundleCoverage = flatten().bundle(classesBytes)
    val coverageCount = Count(bundleCoverage.instructionCounter.coveredCount, totalInstructions)
    val totalCoveragePercent = coverageCount.percentage()

    val scope = this as? Scope
    val coverageByType: Map<String, TestTypeSummary> = when (scope) {
        null -> coveragesByTestType(bundlesByTests, classesBytes, totalInstructions)
        else -> scope.summary.coverage.byTestType
    }
    println(coverageByType)

    val parentVersion = state.buildManager[buildVersion]?.parentVersion ?: ""
    val methodCount = bundleCoverage.methodCounter.toCount(classData.packageTree.totalMethodCount)
    val coverageBlock: Coverage = when (scope) {
        null -> {
            BuildCoverage(
                ratio = totalCoveragePercent,
                count = coverageCount,
                methodCount = methodCount,
                riskCount = zeroCount,
                byTestType = coverageByType,
                diff = otherCoverage?.let {
                    (coverageCount - it).run { first percentOf second  }
                } ?: coverageCount.percentage(),
                prevBuildVersion = parentVersion,
                arrow = otherCoverage?.run { arrowType(coverageCount) },
                finishedScopesCount = scopeCount
            )
        }
        else -> ScopeCoverage(
            ratio = totalCoveragePercent,
            count = coverageCount,
            methodCount = methodCount,
            riskCount = zeroCount,
            byTestType = coverageByType
        )
    }
    println(coverageBlock)

    val methodsChanges = buildInfo?.methodChanges ?: MethodChanges()

    val calculatedMethods = calculateBundleMethods(
        methodsChanges,
        bundleCoverage
    )
    val buildMethods = state.buildTests[state.buildId(buildVersion)]?.let {
        calculatedMethods.copy(
            deletedCoveredMethodsCount = calculatedMethods.deletedMethods.testCount(it.assocTests)
        )
    } ?: calculatedMethods

    val packageCoverage = classData.packageTree.packages.treeCoverage(bundleCoverage, assocTestsMap)

    val (coveredByTest, coveredByTestType) = bundlesByTests.coveredMethods(
        methodsChanges,
        bundlesByTestTypes(classesBytes)
    )

    val testsUsagesInfoByType = coverageByType.map {
        TestsUsagesInfoByType(
            it.value.testType,
            it.value.coverage,
            it.value.coveredMethodsCount,
            bundlesByTests.testUsages(
                totalInstructions,
                it.value.testType
            )
        )
    }.sortedBy { it.testType }

    return CoverageInfoSet(
        associatedTests,
        coverageBlock,
        buildMethods,
        packageCoverage,
        testsUsagesInfoByType,
        coveredByTest,
        coveredByTestType
    )
}

fun Sequence<Session>.coveragesByTestType(
    bundleMap: Map<TypedTest, IBundleCoverage>,
    classesBytes: ClassesBytes,
    totalInstructions: Int
): Map<String, TestTypeSummary> {
    return groupBy(Session::testType).mapValues { (testType, sessions) ->
        sessions.asSequence().run {
            TestTypeSummary(
                testType = testType,
                coverage = flatten().bundle(classesBytes).coverage(totalInstructions),
                testCount = flatMap { it.tests.asSequence() }.distinct().count(),
                coveredMethodsCount = bundleMap.coveredMethodsByTestTypeCount(testType)
            )
        }
    }
}

infix fun Number.percentOf(other: Number): Double = when (val dOther = other.toDouble()) {
    0.0 -> 0.0
    else -> toDouble() * 100.0 / dOther
}

fun Count.percentage(): Double = covered percentOf total

fun Sequence<ExecClassData>.execDataStore(): ExecutionDataStore = map(ExecClassData::toExecutionData)
    .fold(ExecutionDataStore()) { store, execData ->
        store.apply { put(execData) }
    }

internal fun Count.arrowType(other: Count): ArrowType? = (this - other).first.sign.toArrowType()

internal operator fun Count.minus(other: Count): Pair<Long, Long> = takeIf {
    other.total > 0 && other.covered > 0
}?.run {
    total.gcd(other.total).let { gcd ->
        val (totalLong, otherTotalLong) = total.toLong() to other.total.toLong()
        Pair(
            first = (otherTotalLong / gcd * covered) - (totalLong / gcd * other.covered),
            second = totalLong / gcd * (otherTotalLong / gcd)
        )
    }
} ?: covered.toLong() to total.toLong()

private fun Int.toArrowType(): ArrowType? = when (this) {
    in Int.MIN_VALUE..-1 -> ArrowType.INCREASE
    in 1..Int.MAX_VALUE -> ArrowType.DECREASE
    else -> null
}

private fun ExecClassData.toExecutionData() = ExecutionData(id, className, probes.toBooleanArray())

private fun Sequence<Session>.bundlesByTests(
    classesBytes: ClassesBytes
): Map<TypedTest, IBundleCoverage> = takeIf { it.any() }?.run {
    groupBy(Session::testType).map { (testType, sessions) ->
        sessions.asSequence().flatten()
            .groupBy { TypedTest(it.testName, testType) }
            .mapValuesTo(mutableMapOf()) { it.value.asSequence().bundle(classesBytes) }
    }.reduce { m1, m2 ->
        m1.apply { putAll(m2) }
    }
} ?: emptyMap()

private fun Sequence<Session>.bundlesByTestTypes(
    classesBytes: ClassesBytes
): Map<String, IBundleCoverage> = groupBy(Session::testType)
    .mapValues { it.value.asSequence().flatBundle(classesBytes) }


private fun Sequence<Sequence<ExecClassData>>.flatBundle(
    classesBytes: ClassesBytes
): IBundleCoverage = flatten().bundle(classesBytes)

internal fun Sequence<ExecClassData>.bundle(
    classesBytes: ClassesBytes
): IBundleCoverage = bundle { analyzer ->
    contents.forEach { execData ->
        classesBytes[execData.name]?.let { classesBytes ->
            analyzer.analyzeClass(classesBytes, execData.name)
        } ?: println("WARN No class data for ${execData.name}, id=${execData.id}")
    }
}

internal fun ClassesBytes.bundle(
    data: Sequence<ExecClassData> = emptySequence()
): IBundleCoverage = data.bundle { analyzer ->
    forEach { (name, bytes) -> analyzer.analyzeClass(bytes, name) }
}

private fun Sequence<ExecClassData>.bundle(
    analyze: ExecutionDataStore.(Analyzer) -> Unit
): IBundleCoverage = CoverageBuilder().also { coverageBuilder ->
    val dataStore = execDataStore()
    val analyzer = Analyzer(dataStore, coverageBuilder)
    dataStore.analyze(analyzer)
}.getBundle("")

private fun Map<TypedTest, IBundleCoverage>.associatedTests(): Map<CoverageKey, List<TypedTest>> = run {
    entries.asSequence()
        .flatMap { (test, bundle) ->
            bundle.coverageKeys().map { it to test }
        }.distinct()
        .groupBy({ it.first }) { it.second }
}

private fun Map<TypedTest, IBundleCoverage>.coveredMethodsByTestTypeCount(
    testType: String
): Int = entries.asSequence()
    .filter { it.key.type == testType }
    .flatMap { it.value.coverageKeys() }
    .filter(CoverageKey::isMethod)
    .distinct()
    .count()

private fun IBundleCoverage.coverageKeys(): Sequence<CoverageKey> = packages.asSequence().flatMap { p ->
    sequenceOf(p.coverageKey()) + p.classes.asSequence().flatMap { c ->
        sequenceOf(c.coverageKey()) + c.methods.asSequence().mapNotNull { m ->
            m.takeIf { it.instructionCounter.coveredCount > 0 }?.coverageKey(c)
        }
    }
}
