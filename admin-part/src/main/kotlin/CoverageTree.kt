package com.epam.drill.plugins.test2code

import com.epam.drill.common.*
import com.epam.drill.plugins.test2code.api.*
import com.epam.drill.plugins.test2code.common.api.*
import com.epam.drill.plugins.test2code.coverage.*

internal fun Iterable<AstEntity>.toPackages(): List<JavaPackageCoverage> = run {
    groupBy(AstEntity::path).entries.map { (path, astEntities) ->
        JavaPackageCoverage(
            id = path.crc64,
            name = path,
            totalClassesCount = astEntities.count(),
            totalMethodsCount = astEntities.flatMap(AstEntity::methodsWithProbes).count(),
            totalCount = astEntities.flatMap(AstEntity::methodsWithProbes).map(AstMethod::count).sum(),
            classes = astEntities.mapNotNull { ast ->
                ast.methodsWithProbes().takeIf { it.any() }?.let { methods ->
                    JavaClassCoverage(
                        id = "$path.${ast.name}".crc64,
                        name = ast.name,
                        path = path,
                        totalMethodsCount = methods.count(),
                        totalCount = methods.sumBy { it.count },
                        methods = methods.fold(listOf()) { acc, astMethod ->
                            acc + JavaMethodCoverage(
                                id = "$path.${ast.name}.${astMethod.name}".crc64,
                                name = astMethod.name,
                                desc = "",
                                count = astMethod.probes.size,
                                decl = astMethod.params.joinToString(
                                    prefix = "(",
                                    postfix = "):${astMethod.returnType}"
                                ),
                                probeRange = (acc.lastOrNull()?.probeRange?.last?.inc() ?: 0).let {
                                    ProbeRange(it, it + astMethod.probes.lastIndex)
                                }
                            )
                        },
                        probes = methods.flatMap(AstMethod::probes)
                    )
                }
            }
        )
    }
}

internal fun BundleCounter.toPackages(
    parsedClasses: Map<String, Methods>
): List<JavaPackageCoverage> = packages.map { packageCoverage ->
    val classes = packageCoverage.classes.classTree(parsedClasses)
    JavaPackageCoverage(
        id = packageCoverage.coverageKey().id,
        name = packageCoverage.name,
        totalClassesCount = classes.count(),
        totalMethodsCount = classes.sumBy { it.totalMethodsCount },
        totalCount = packageCoverage.count.total,
        classes = classes
    )
}.toList()


internal fun Iterable<JavaPackageCoverage>.treeCoverage(
    bundle: BundleCounter,
    assocTestsMap: Map<CoverageKey, List<TypedTest>>
): List<JavaPackageCoverage> = run {
    val bundleMap = bundle.packages.associateBy { it.coverageKey().id }
    map { pkg ->
        bundleMap[pkg.id]?.run {
            pkg.copy(
                coverage = count.copy(total = pkg.totalCount).percentage(),
                coveredClassesCount = classCount.covered,
                coveredMethodsCount = methodCount.covered,
                assocTestsCount = assocTestsMap[coverageKey()]?.count() ?: 0,
                classes = pkg.classes.classCoverage(classes, assocTestsMap)
            )
        } ?: pkg
    }
}

private fun Collection<ClassCounter>.classTree(
    parsedClasses: Map<String, Methods>
): List<JavaClassCoverage> = map { classCoverage ->
    val classKey = classCoverage.coverageKey()
    val parsedMethods = parsedClasses[classCoverage.fullName] ?: emptyList()
    val methods = classCoverage.toMethodCoverage { methodCov ->
        parsedMethods.any { it.name == methodCov.name && it.desc == methodCov.desc }
    }
    JavaClassCoverage(
        id = classKey.id,
        name = classCoverage.name.toShortClassName(),
        path = classCoverage.name,
        totalMethodsCount = methods.count(),
        totalCount = methods.sumBy { it.count },
        methods = methods,
        probes = emptyList()
    )
}.toList()

private fun List<JavaClassCoverage>.classCoverage(
    classCoverages: Collection<ClassCounter>,
    assocTestsMap: Map<CoverageKey, List<TypedTest>>
): List<JavaClassCoverage> = run {
    val bundleMap = classCoverages.associateBy { it.coverageKey().id }
    map { classCov ->
        bundleMap[classCov.id]?.run {
            classCov.copy(
                coverage = count.percentage(),
                coveredMethodsCount = methods.count { it.count.covered > 0 },
                assocTestsCount = assocTestsMap[coverageKey()]?.count() ?: 0,
                methods = toMethodCoverage(assocTestsMap)
            )
        } ?: classCov
    }
}

internal fun ClassCounter.toMethodCoverage(
    assocTestsMap: Map<CoverageKey, List<TypedTest>> = emptyMap(),
    filter: (MethodCounter) -> Boolean = { true }
): List<JavaMethodCoverage> {
    return methods.filter(filter).map { methodCoverage ->
        val methodKey = methodCoverage.coverageKey(this)
        JavaMethodCoverage(
            id = methodKey.id,
            name = name.methodName(methodCoverage.name),
            desc = methodCoverage.desc,
            decl = methodCoverage.decl,
            coverage = methodCoverage.count.percentage(),
            count = methodCoverage.count.total,
            assocTestsCount = assocTestsMap[methodKey]?.count() ?: 0
        )
    }.toList()
}

private fun AstEntity.methodsWithProbes(): List<AstMethod> = methods.filter { it.probes.any() }
