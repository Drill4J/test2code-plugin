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
fun ClassData.calculateBundleMethods(
    bundleCoverage: BundleCounter,
    onlyCovered: Boolean = false
): BuildMethods = methods.toCoverMap(bundleCoverage, onlyCovered).let { covered ->
    BuildMethods(
        totalMethods = covered.keys.toInfo(covered),
        newMethods = methodChanges.new.toInfo(covered),
        allModifiedMethods = methodChanges.modified.toInfo(covered),
        unaffectedMethods = methodChanges.unaffected.toInfo(covered),
        deletedMethods = methodChanges.deleted.toInfo(covered)
    )
}

fun Map<TypedTest, BundleCounter>.coveredMethods(
    classData: ClassData,
    bundlesByType: Map<String, BundleCounter>
): Pair<List<MethodsCoveredByTest>, List<MethodsCoveredByTestType>> {
    val coveredByTest = map { (typedTest, bundle) ->
        val changes = classData.calculateBundleMethods(bundle, true)
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
        val changes = classData.calculateBundleMethods(bundle, true)
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

fun String.methodName(name: String): String = when (name) {
    "<init>" -> toShortClassName()
    "<clinit>" -> "static ${toShortClassName()}"
    else -> name
}

private fun Iterable<Method>.toInfo(
    covered: Map<Method, CoverMethod>
) = MethodsInfo(
    totalCount = count(),
    coveredCount = count { covered[it]?.count?.covered ?: 0 > 0 },
    methods = mapNotNull(covered::get)
)
