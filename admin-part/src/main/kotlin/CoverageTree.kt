package com.epam.drill.plugins.test2code

import com.epam.drill.common.*
import com.epam.drill.plugins.test2code.api.*
import com.epam.drill.plugins.test2code.common.api.*
import org.jacoco.core.analysis.*

internal fun IBundleCoverage.toPackages(
    parsedClasses: Map<String, Methods>
): List<JavaPackageCoverage> = packages.map { packageCoverage ->
    val classes = packageCoverage.classes.classTree(parsedClasses)
    JavaPackageCoverage(
        id = packageCoverage.coverageKey().id,
        name = packageCoverage.name,
        totalClassesCount = classes.count(),
        totalMethodsCount = classes.sumBy { it.totalMethodsCount },
        totalCount = packageCoverage.instructionCounter.totalCount,
        classes = classes
    )
}.toList()

internal fun Iterable<AstEntity>.toPackages(): List<JavaPackageCoverage> = run {
    groupBy(AstEntity::path).entries.map { (path, astEntities) ->
        JavaPackageCoverage(
            id = path.crc64,
            name = path,
            totalClassesCount = astEntities.count(),
            totalMethodsCount = astEntities.flatMap(AstEntity::methods).count(),
            totalCount = astEntities.flatMap(AstEntity::methods).map(AstMethod::count).sum(),
            classes = astEntities.map { ast ->
                JavaClassCoverage(
                    id = "$path.${ast.name}".crc64,
                    name = ast.name,
                    path = path,
                    totalMethodsCount = ast.methods.count(),
                    totalCount = ast.methods.sumBy { it.count },
                    methods = ast.methods.map { astMethod ->
                        JavaMethodCoverage(
                            id = "$path.${ast.name}.${astMethod.name}".crc64,
                            name = astMethod.name,
                            desc = "",
                            count = astMethod.count,
                            decl = astMethod.params.joinToString(prefix = "(", postfix = "):${astMethod.returnType}")
                        )
                    }
                )
            }
        )
    }
}

internal fun Iterable<JavaPackageCoverage>.treeCoverage(
    bundle: IBundleCoverage,
    assocTestsMap: Map<CoverageKey, List<TypedTest>>
): List<JavaPackageCoverage> = run {
    val bundleMap = bundle.packages.associateBy { it.coverageKey().id }
    map { pkg ->
        bundleMap[pkg.id]?.run {
            pkg.copy(
                coverage = coverage(pkg.totalCount),
                coveredClassesCount = classCounter.coveredCount,
                coveredMethodsCount = methodCounter.coveredCount,
                assocTestsCount = assocTestsMap[coverageKey()]?.count() ?: 0,
                classes = pkg.classes.classCoverage(classes, assocTestsMap)
            )
        } ?: pkg
    }
}

private fun Collection<IClassCoverage>.classTree(
    parsedClasses: Map<String, Methods>
): List<JavaClassCoverage> = map { classCoverage ->
    val classKey = classCoverage.coverageKey()
    val parsedMethods = parsedClasses[classCoverage.name] ?: emptyList()
    val methods = classCoverage.toMethodCoverage { methodCov ->
        parsedMethods.any { it.name == methodCov.name && it.desc == methodCov.desc }
    }
    JavaClassCoverage(
        id = classKey.id,
        name = classCoverage.name.toShortClassName(),
        path = classCoverage.name,
        totalMethodsCount = methods.count(),
        totalCount = methods.sumBy { it.count },
        methods = methods
    )
}.toList()

private fun List<JavaClassCoverage>.classCoverage(
    classCoverages: Collection<IClassCoverage>,
    assocTestsMap: Map<CoverageKey, List<TypedTest>>
): List<JavaClassCoverage> = run {
    val bundleMap = classCoverages.associateBy { it.coverageKey().id }
    map { classCov ->
        bundleMap[classCov.id]?.run {
            classCov.copy(
                coverage = coverage(),
                coveredMethodsCount = methodCounter.coveredCount,
                assocTestsCount = assocTestsMap[coverageKey()]?.count() ?: 0,
                methods = toMethodCoverage(assocTestsMap)
            )
        } ?: classCov
    }
}

internal fun IClassCoverage.toMethodCoverage(
    assocTestsMap: Map<CoverageKey, List<TypedTest>> = emptyMap(),
    filter: (IMethodCoverage) -> Boolean = { true }
): List<JavaMethodCoverage> {
    return methods.filter(filter).map { methodCoverage ->
        val methodKey = methodCoverage.coverageKey(this)
        JavaMethodCoverage(
            id = methodKey.id,
            name = name.methodName(methodCoverage.name),
            desc = methodCoverage.desc,
            decl = declaration(methodCoverage.desc),
            coverage = methodCoverage.coverage(),
            count = methodCoverage.instructionCounter.totalCount,
            assocTestsCount = assocTestsMap[methodKey]?.count() ?: 0
        )
    }.toList()
}
