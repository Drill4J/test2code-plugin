package com.epam.drill.plugins.coverage

import org.jacoco.core.analysis.*

//TODO Rewrite all of this, remove the file

data class CoverageInfoSet(
    val associatedTests: List<AssociatedTests>,
    val coverageBlock: CoverageBlock,
    val coverageByType: Map<String, TestTypeSummary>,
    val newCoverageBlock: NewCoverageBlock,
    val newMethodsCoverages: List<SimpleJavaMethodCoverage>,
    val packageCoverage: List<JavaPackageCoverage>,
    val testUsages: List<TestUsagesInfo>
)

fun testUsages(bundleMap: Map<String, IBundleCoverage>): List<TestUsagesInfo> =
    bundleMap.map { (k, v) ->
        //TODO !!!!!!!!!!!!s
        val (name, type) = k.split("::").let { it[1] to it[0] }
        TestUsagesInfo(name, v.methodCounter.coveredCount, type, "30.02.2019")
    }

fun packageCoverage(
    bundleCoverage: IBundleCoverage,
    assocTestsMap: Map<CoverageKey, List<String>>
): List<JavaPackageCoverage> = bundleCoverage.packages
    .map { packageCoverage ->
        val packageKey = packageCoverage.coverageKey()
        JavaPackageCoverage(
            id = packageKey.id,
            name = packageCoverage.name,
            coverage = packageCoverage.coverage,
            totalClassesCount = packageCoverage.classCounter.totalCount,
            coveredClassesCount = packageCoverage.classCounter.coveredCount,
            totalMethodsCount = packageCoverage.methodCounter.totalCount,
            coveredMethodsCount = packageCoverage.methodCounter.coveredCount,
            assocTestsCount = assocTestsMap[packageKey]?.count(),
            classes = classCoverage(packageCoverage.classes, assocTestsMap)
        )
    }.toList()

fun classCoverage(
    classCoverages: Collection<IClassCoverage>,
    assocTestsMap: Map<CoverageKey, List<String>>
): List<JavaClassCoverage> = classCoverages
    .map { classCoverage ->
        val classKey = classCoverage.coverageKey()
        JavaClassCoverage(
            id = classKey.id,
            name = classCoverage.name.substringAfterLast('/'),
            path = classCoverage.name,
            coverage = classCoverage.coverage,
            totalMethodsCount = classCoverage.methodCounter.totalCount,
            coveredMethodsCount = classCoverage.methodCounter.coveredCount,
            assocTestsCount = assocTestsMap[classKey]?.count(),
            methods = classCoverage.methods.map { methodCoverage ->
                val methodKey = methodCoverage.coverageKey(classCoverage)
                JavaMethodCoverage(
                    id = methodKey.id,
                    name = methodCoverage.name,
                    desc = methodCoverage.desc,
                    decl = declaration(methodCoverage.desc),
                    coverage = methodCoverage.coverage,
                    assocTestsCount = assocTestsMap[methodKey]?.count()
                )
            }.toList()
        )
    }.toList()

fun Map<CoverageKey, List<String>>.getAssociatedTests() = map { (key, tests) ->
    AssociatedTests(
        id = key.id,
        packageName = key.packageName,
        className = key.className,
        methodName = key.methodName,
        tests = tests
    )
}

fun calculateNewCoverageBlock(
    newMethods: List<JavaMethod>,
    bundleCoverage: IBundleCoverage
) = if (newMethods.isNotEmpty()) {
    println("New methods count: ${newMethods.count()}")
    val newMethodSet = newMethods.toSet()
    val newMethodsCoverages = bundleCoverage.packages
        .flatMap { it.classes }
        .flatMap { c -> c.methods.map { Pair(JavaMethod(c.name, it.name, it.desc), it) } }
        .filter { it.first in newMethodSet }
    val totalCount = newMethodsCoverages.sumBy { it.second.instructionCounter.totalCount }
    val coveredCount = newMethodsCoverages.sumBy { it.second.instructionCounter.coveredCount }
    //bytecode instruction coverage
    val newCoverage = if (totalCount > 0) coveredCount.toDouble() / totalCount * 100 else 0.0

    val coverages = newMethodsCoverages.map { (jm, mc) ->
        mc.simpleMethodCoverage(jm.ownerClass)
    }
    NewCoverageBlock(
        methodsCount = newMethodsCoverages.count(),
        methodsCovered = newMethodsCoverages.count { it.second.methodCounter.coveredCount > 0 },
        coverage = newCoverage
    ) to coverages
} else NewCoverageBlock() to emptyList()