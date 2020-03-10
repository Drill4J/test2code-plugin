package com.epam.drill.plugins.test2code

import com.epam.drill.common.*
import org.jacoco.core.analysis.*
import org.jacoco.core.data.*
import kotlin.math.*

typealias ClassesBytes = Map<String, ByteArray>

internal fun ActiveScope.calcSummary(
    state: PluginInstanceState,
    finishedSession: Sequence<FinishedSession> = emptySequence()
): ScopeSummary {
    val classesData = state.data as ClassesData
    val classesBytes = state.buildInfo?.classesBytes
    val totalInstructions = classesData.totalInstructions
    return summary.copy(
        coverage = classesBytes?.coverage(this + finishedSession, totalInstructions) ?: 0.0,
        coveragesByType = classesBytes?.coveragesByTestType(
            bundlesByTests(classesBytes),
            this,
            totalInstructions
        ) ?: emptyMap()
    )
}

internal suspend fun Sequence<FinishedSession>.calculateCoverageData(
    state: PluginInstanceState,
    buildVersion: String
): CoverageInfoSet {
    val buildInfo = state.buildManager[buildVersion]
    val classesBytes: ClassesBytes = buildInfo?.classesBytes ?: emptyMap()
    val classesData = state.classesData(buildVersion) as ClassesData
    val probes = flatten()
    // Analyze all existing classes
    val coverageBuilder = CoverageBuilder()
    val dataStore = probes.execDataStore()
    val initialClassBytes = buildInfo?.classesBytes ?: emptyMap()
    val analyzer = Analyzer(dataStore, coverageBuilder)

    val bundleMap = bundlesByTests(classesBytes)
    val assocTestsMap = bundleMap.associatedTests()
    val associatedTests = assocTestsMap.getAssociatedTests()

    initialClassBytes.forEach { (name, bytes) ->
        analyzer.analyzeClass(bytes, name)
    }
    val bundleCoverage = coverageBuilder.getBundle("")
    val totalCoveragePercent = bundleCoverage.coverage(classesData.totalInstructions)

    val scope = this as? Scope
    val coverageByType: Map<String, TestTypeSummary> = when (scope) {
        null -> classesBytes.coveragesByTestType(bundleMap, this, classesData.totalInstructions)
        else -> scope.summary.coveragesByType
    }
    println(coverageByType)

    val coverageBlock: Coverage = when (scope) {
        null -> {
            val prevBuildVersion = classesData.prevBuildVersion
            BuildCoverage(
                coverage = totalCoveragePercent,
                diff = totalCoveragePercent - classesData.prevBuildCoverage,
                prevBuildVersion = prevBuildVersion,
                coverageByType = coverageByType,
                arrow = if (prevBuildVersion.isNotBlank()) classesData.arrowType(totalCoveragePercent) else null,
                finishedScopesCount = state.scopeManager.scopes(buildVersion).count()
            )
        }
        else -> ScopeCoverage(
            totalCoveragePercent,
            coverageByType
        )
    }
    println(coverageBlock)

    val methodsChanges = buildInfo?.methodChanges ?: MethodChanges()

    val calculatedMethods = calculateBundleMethods(
        methodsChanges,
        bundleCoverage
    )
    val buildMethods = calculatedMethods.copy(
        deletedCoveredMethodsCount = calculatedMethods.deletedMethods.testCount(
            state.buildTests,
            classesData.prevBuildVersion
        )
    )
    val packageCoverage = bundleCoverage.packageCoverage(assocTestsMap)

    val (coveredByTest, coveredByTestType) = bundleMap.coveredMethods(
        methodsChanges,
        bundlesByTestTypes(classesBytes)
    )

    val testsUsagesInfoByType = coverageByType.map {
        TestsUsagesInfoByType(
            it.value.testType,
            it.value.coverage,
            it.value.coveredMethodsCount,
            testUsages(
                bundlesByTests(classesBytes),
                classesData.totalInstructions,
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

fun Sequence<FinishedSession>.coverage(classesBytes: ClassesBytes, totalInstructions: Int) =
    flatten().coverageBundle(classesBytes).coverage(totalInstructions)


fun ClassesBytes.coverage(data: Sequence<FinishedSession>, totalInstructions: Int) =
    data.flatten().coverageBundle(this).coverage(totalInstructions)

fun ClassesBytes.coveragesByTestType(
    bundleMap: Map<TypedTest, IBundleCoverage>,
    data: Sequence<FinishedSession>,
    totalInstructions: Int
): Map<String, TestTypeSummary> {
    return data.groupBy { it.testType }.mapValues { (testType, finishedSessions) ->
        TestTypeSummary(
            testType = testType,
            coverage = coverage(finishedSessions.asSequence(), totalInstructions),
            testCount = finishedSessions.flatMap { it.testNames }.distinct().count(),
            coveredMethodsCount = bundleMap.coveredMethodsByTestTypeCount(testType)
        )
    }
}

fun Sequence<ExecClassData>.execDataStore(): ExecutionDataStore = map { it.toExecutionData() }
    .fold(ExecutionDataStore()) { store, execData ->
        store.apply { put(execData) }
    }

private fun ExecClassData.toExecutionData() = ExecutionData(id, className, probes.toBooleanArray())

private fun ClassesData.arrowType(totalCoveragePercent: Double): ArrowType? {
    val diff = totalCoveragePercent - prevBuildCoverage
    return when {
        abs(diff) < 1E-7 -> null
        diff > 0.0 -> ArrowType.INCREASE
        else -> ArrowType.DECREASE
    }
}

private fun Sequence<FinishedSession>.bundlesByTests(
    classesBytes: ClassesBytes
): Map<TypedTest, IBundleCoverage> = flatMap { it.probes.asSequence() }
    .groupBy({ it.key }) { it.value }
    .mapValues { it.value.asSequence().flatten() }
    .mapValues { it.value.coverageBundle(classesBytes) }

private fun Sequence<FinishedSession>.bundlesByTestTypes(
    classesBytes: ClassesBytes
): Map<String, IBundleCoverage> = flatMap { it.probes.asSequence() }
    .groupBy({ it.key.type }) { it.value }
    .mapValues { it.value.asSequence().flatten() }
    .mapValues { it.value.coverageBundle(classesBytes) }

private fun Sequence<ExecClassData>.coverageBundle(
    classesBytes: ClassesBytes
): IBundleCoverage = CoverageBuilder().also { coverageBuilder ->
    val dataStore = execDataStore()
    val analyzer = Analyzer(dataStore, coverageBuilder)
    dataStore.contents.forEach {
        analyzer.analyzeClass(classesBytes[it.name], it.name)
    }
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
