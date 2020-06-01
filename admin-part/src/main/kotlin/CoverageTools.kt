package com.epam.drill.plugins.test2code

import com.epam.drill.common.*
import com.epam.drill.plugins.test2code.api.*
import com.epam.drill.plugins.test2code.coverage.*

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

fun Map<TypedTest, BundleCounter>.testUsages(
    totalCoverageCount: Int,
    testType: String
): List<TestUsagesInfo> = filter { it.key.type == testType }
    .map { (test, bundle) ->
        TestUsagesInfo(
            id = test.id(),
            testName = test.name,
            methodCalls = bundle.methodCount.covered,
            coverage = bundle.count.copy(total = totalCoverageCount).percentage()
        )
    }.sortedBy { it.testName }

fun Map<CoverageKey, List<TypedTest>>.getAssociatedTests() = map { (key, tests) ->
    AssociatedTests(
        id = key.id,
        packageName = key.packageName,
        className = key.className,
        methodName = key.className.methodName(key.methodName),
        tests = tests.sortedBy { it.name }
    )
}.sortedBy { it.methodName }

//TODO rewrite this
fun calculateBundleMethods(
    methodChanges: MethodChanges,
    bundleCoverage: BundleCounter,
    excludeMissed: Boolean = false
): BuildMethods {
    val methodsCoverages = bundleCoverage.toDataMap()

    val infos: Map<DiffType, MethodsInfo> = DiffType.values().asSequence().map { diffType ->
        diffType to (methodChanges.map[diffType]?.getInfo(methodsCoverages, excludeMissed) ?: MethodsInfo())
    }.toMap()

    val totalInfo: MethodsInfo = infos.asSequence().filter { it.key != DiffType.DELETED }
        .map { it.value }
        .reduce { totalInfo, info ->
            MethodsInfo(
                totalInfo.totalCount + info.totalCount,
                totalInfo.coveredCount + info.coveredCount,
                totalInfo.methods + info.methods
            )
        }

    val modifiedNameMethods = infos.getValue(DiffType.MODIFIED_NAME)
    val modifiedDescMethods = infos.getValue(DiffType.MODIFIED_DESC)
    val modifiedBodyMethods = infos.getValue(DiffType.MODIFIED_BODY)
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
        newMethods = infos.getValue(DiffType.NEW),
        modifiedNameMethods = modifiedNameMethods,
        modifiedDescMethods = modifiedDescMethods,
        modifiedBodyMethods = modifiedBodyMethods,
        allModifiedMethods = allModifiedMethods,
        unaffectedMethods = infos.getValue(DiffType.UNAFFECTED),
        deletedMethods = infos.getValue(DiffType.DELETED)
    )
}

fun Methods.getInfo(
    data: Map<Pair<String, String>, MethodCounter>,
    excludeMissed: Boolean
) = MethodsInfo(
    totalCount = count(),
    coveredCount = count { data[it.ownerClass to it.sign]?.count?.covered ?: 0 > 0 },
    methods = mapNotNull { method ->
        val coverageRate = data[method.ownerClass to method.sign]?.coverageRate() ?: CoverageRate.MISSED
        coverageRate.takeIf { !excludeMissed || it != CoverageRate.MISSED }?.let {
            JavaMethod(
                ownerClass = method.ownerClass,
                name = method.ownerClass.methodName(method.name),
                desc = method.desc.takeIf { "):" in it } ?: declaration(method.desc), //TODO js methods
                hash = method.hash,
                coverageRate = coverageRate
            )
        }
    }.sortedBy { it.name }
)

fun Map<TypedTest, BundleCounter>.coveredMethods(
    methodChanges: MethodChanges,
    bundlesByType: Map<String, BundleCounter>
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

fun String.methodName(name: String): String = when (name) {
    "<init>" -> toShortClassName()
    "<clinit>" -> "static ${toShortClassName()}"
    else -> name
}
