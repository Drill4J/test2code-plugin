package com.epam.drill.plugins.test2code

import com.epam.drill.common.*
import com.epam.drill.plugins.test2code.api.*
import org.jacoco.core.analysis.*

//TODO Rewrite all of this, remove the file

data class CoverageInfoSet(
    val associatedTests: List<AssociatedTests>,
    val coverage: Coverage,
    val buildMethods: BuildMethods = BuildMethods(),
    val packageCoverage: List<JavaPackageCoverage> = emptyList(),
    val testsUsagesInfoByType: List<TestsUsagesInfoByType> = emptyList(),
    val methodsCoveredByTest: List<MethodsCoveredByTest> = emptyList(),
    val methodsCoveredByTestType: List<MethodsCoveredByTestType> = emptyList()
)

fun Map<TypedTest, IBundleCoverage>.testUsages(
    totalCoverageCount: Int,
    testType: String
): List<TestUsagesInfo> = filter { it.key.type == testType }
    .map { (test, bundle) ->
        TestUsagesInfo(test.id(), test.name, bundle.methodCounter.coveredCount, bundle.coverage(totalCoverageCount))
    }.sortedBy { it.testName }

fun Map<CoverageKey, List<TypedTest>>.getAssociatedTests() = map { (key, tests) ->
    AssociatedTests(
        id = key.id,
        packageName = key.packageName,
        className = key.className,
        methodName = key.className?.methodName(key.methodName),
        tests = tests
    )
}.sortedBy { it.methodName }.map { assocTests -> assocTests.copy(tests = assocTests.tests.sortedBy { it.name }) }

fun IBundleCoverage.toDataMap() = packages
    .flatMap { it.classes }
    .flatMap { c -> c.methods.map { (c.name to it.sign()) to it } }.toMap()

//TODO rewrite this
fun calculateBundleMethods(
    methodChanges: MethodChanges,
    bundleCoverage: IBundleCoverage,
    excludeMissed: Boolean = false
): BuildMethods {
    val methodsCoverages = bundleCoverage.toDataMap()

    val infos: Map<DiffType, MethodsInfo> = DiffType.values().map { type ->
        type to (methodChanges.map[type]?.getInfo(methodsCoverages, excludeMissed) ?: MethodsInfo())
    }.toMap()

    val totalInfo: MethodsInfo = infos.filter { it.key != DiffType.DELETED }.values
        .reduce { totalInfo, info ->
            MethodsInfo(
                totalInfo.totalCount + info.totalCount,
                totalInfo.coveredCount + info.coveredCount,
                totalInfo.methods + info.methods
            )
        }

    val modifiedNameMethods = infos[DiffType.MODIFIED_NAME]!!
    val modifiedDescMethods = infos[DiffType.MODIFIED_DESC]!!
    val modifiedBodyMethods = infos[DiffType.MODIFIED_BODY]!!
    val allModifiedMethods = listOf(modifiedBodyMethods, modifiedDescMethods, modifiedNameMethods)
        .flatMap(MethodsInfo::methods)
        .let { methods ->
            MethodsInfo(
                totalCount = methods.count(),
                coveredCount = methods.count { it.coverageRate != CoverageRate.MISSED },
                methods = methods
            )
        }
    return BuildMethods(
        totalMethods = totalInfo,
        newMethods = infos[DiffType.NEW]!!,
        modifiedNameMethods = modifiedNameMethods,
        modifiedDescMethods = modifiedDescMethods,
        modifiedBodyMethods = modifiedBodyMethods,
        allModifiedMethods = allModifiedMethods,
        unaffectedMethods = infos[DiffType.UNAFFECTED]!!,
        deletedMethods = infos[DiffType.DELETED]!!
    )
}

fun Methods.getInfo(
    data: Map<Pair<String, String>, IMethodCoverage>,
    excludeMissed: Boolean
) = MethodsInfo(
    totalCount = this.count(),
    coveredCount = count { data[it.ownerClass to it.sign]?.instructionCounter?.coveredCount ?: 0 > 0 },
    methods = this.mapNotNull { method ->
        val coverageRate = data[method.ownerClass to method.sign]?.coverageRate() ?: CoverageRate.MISSED
        if (!(excludeMissed && coverageRate == CoverageRate.MISSED)) {
            JavaMethod(
                ownerClass = method.ownerClass,
                name = method.ownerClass.methodName(method.name) ?: "",
                desc = declaration(method.desc),
                hash = method.hash,
                coverageRate = coverageRate
            )
        } else null
    }.sortedBy { it.name }
)

fun Map<TypedTest, IBundleCoverage>.coveredMethods(
    methodChanges: MethodChanges,
    bundlesByType: Map<String, IBundleCoverage>
): Pair<List<MethodsCoveredByTest>, List<MethodsCoveredByTestType>> {
    val coveredByTest = map { (typedTest, bundle) ->
        val changes = calculateBundleMethods(methodChanges, bundle, true)
        MethodsCoveredByTest(
            id = typedTest.id(),
            testName = typedTest.name,
            testType = typedTest.type,
            newMethods = changes.newMethods.methods,
            modifiedMethods = changes.allModifiedMethods.methods,
            unaffectedMethods = changes.unaffectedMethods.methods
        )
    }
    val typesCounts = keys.groupBy { it.type }.mapValues { it.value.count() }
    val coveredByType = bundlesByType.map { (type, bundle) ->
        val changes = calculateBundleMethods(methodChanges, bundle, true)
        MethodsCoveredByTestType(
            testType = type,
            testsCount = typesCounts[type] ?: 0,
            newMethods = changes.newMethods.methods,
            modifiedMethods = changes.allModifiedMethods.methods,
            unaffectedMethods = changes.unaffectedMethods.methods
        )
    }
    return coveredByTest to coveredByType
}

private val Method.sign get() = "$name$desc"

fun IMethodCoverage.sign() = "$name$desc"

fun String.methodName(name: String?): String? = when (name) {
    "<init>" -> toShortClassName()
    "<clinit>" -> "static ${toShortClassName()}"
    else -> name
}

fun String.toShortClassName(): String = substringAfterLast('/')
