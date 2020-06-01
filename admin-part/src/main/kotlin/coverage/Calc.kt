package com.epam.drill.plugins.test2code.coverage

import com.epam.drill.plugins.test2code.*
import com.epam.drill.plugins.test2code.api.*
import com.epam.drill.plugins.test2code.common.api.*

internal fun Sequence<ExecClassData>.bundle(
    tree: PackageTree
): BundleCounter = run {
    val probesByClasses = groupBy(ExecClassData::className).mapValues {
        it.value.reduce { acc, execClassData -> acc.merge(execClassData) }.probes
    }
    val classMethods = tree.packages.flatMap { it.classes }.associate {
        "${it.path}/${it.name}" to it.methods
    }
    val covered = probesByClasses.values.sumBy { probes -> probes.count { it } }
    val packages = groupBy({ it.className.substringBeforeLast("/") }) { it.className }
    BundleCounter(
        name = "",
        methodCount = zeroCount,
        count = Count(covered, tree.totalCount),
        packages = packages.map { (pkgName, classNames) ->
            PackageCounter(
                name = pkgName,
                count = classNames.flatMap { probesByClasses[it] ?: emptyList() }.toCount(),
                classCount = Count(
                    classNames.count { name -> probesByClasses.getValue(name).any { it } },
                    classNames.size
                ),
                methodCount = Count(0, classNames.sumBy { classMethods.getValue(it).count() }),
                classes = classNames.map { className ->
                    val probes = probesByClasses.getValue(className)
                    ClassCounter(
                        path = pkgName,
                        name = className.toShortClassName(),
                        count = probes.toCount(),
                        methods = classMethods.getValue(className).map {
                            val methodProbes = probes.slice(it.probeRange)
                            MethodCounter(it.name, it.desc, it.decl, methodProbes.toCount())
                        }
                    )
                }
            )
        }
    )
}
