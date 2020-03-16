package com.epam.drill.plugins.test2code

import org.jacoco.core.analysis.*

internal fun IBundleCoverage.packageTree(): List<JavaPackageCoverage> = packages.map { packageCoverage ->
    JavaPackageCoverage(
        id = packageCoverage.coverageKey().id,
        name = packageCoverage.name,
        totalClassesCount = packageCoverage.classCounter.totalCount,
        totalMethodsCount = packageCoverage.methodCounter.totalCount,
        classes = packageCoverage.classes.classTree()
    )
}.toList()

internal fun Iterable<JavaPackageCoverage>.treeCoverage(
    bundle: IBundleCoverage,
    assocTestsMap: Map<CoverageKey, List<TypedTest>>
): List<JavaPackageCoverage> = run {
    val packageItr = bundle.packages.iterator()
    var cvg = packageItr.nextOrNull()
    map { pkg ->
        val pkgCvg = cvg
        val key = pkgCvg?.coverageKey()
        if (pkgCvg != null && pkg.id == key?.id) {
            pkg.copy(
                coverage = pkgCvg.coverage(),
                coveredClassesCount = pkgCvg.classCounter.coveredCount,
                coveredMethodsCount = pkgCvg.methodCounter.coveredCount,
                assocTestsCount = assocTestsMap[key]?.count(),
                classes = pkg.classes.classCoverage(pkgCvg.classes, assocTestsMap)
            ).also { cvg = packageItr.nextOrNull() }
        } else pkg
    }
}

private fun Collection<IClassCoverage>.classTree(
    assocTestsMap: Map<CoverageKey, List<TypedTest>> = emptyMap()
): List<JavaClassCoverage> = map { classCoverage ->
    val classKey = classCoverage.coverageKey()
    JavaClassCoverage(
        id = classKey.id,
        name = classCoverage.name.toShortClassName(),
        path = classCoverage.name,
        totalMethodsCount = classCoverage.methodCounter.totalCount,
        methods = classCoverage.toMethodCoverage(assocTestsMap)
    )
}.toList()

private fun List<JavaClassCoverage>.classCoverage(
    classCoverages: Collection<IClassCoverage>,
    assocTestsMap: Map<CoverageKey, List<TypedTest>>
): List<JavaClassCoverage> = run {
    val itr = classCoverages.iterator()
    var next = itr.nextOrNull()
    map { classCov ->
        next?.run { coverageKey() to this }
            ?.takeIf { it.first.id == classCov.id }?.let { (key, cov) ->
                classCov.copy(
                    coverage = cov.coverage(),
                    coveredMethodsCount = cov.methodCounter.coveredCount,
                    assocTestsCount = assocTestsMap[key]?.count(),
                    methods = cov.toMethodCoverage(assocTestsMap)
                ).also { next = itr.nextOrNull() }
            } ?: classCov
    }
}

internal fun IClassCoverage.toMethodCoverage(
    assocTestsMap: Map<CoverageKey, List<TypedTest>>,
    total: Int = 0
): List<JavaMethodCoverage> {
    return methods.map { methodCoverage ->
        val methodKey = methodCoverage.coverageKey(this)
        JavaMethodCoverage(
            id = methodKey.id,
            name = name.methodName(methodCoverage.name) ?: "",
            desc = methodCoverage.desc,
            decl = declaration(methodCoverage.desc),
            coverage = methodCoverage.coverage(total),
            assocTestsCount = assocTestsMap[methodKey]?.count()
        )
    }.toList()
}

private fun <T> Iterator<T>.nextOrNull() = takeIf(Iterator<*>::hasNext)?.next()
