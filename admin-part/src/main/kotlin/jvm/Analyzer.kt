package com.epam.drill.plugins.test2code.jvm

import com.epam.drill.plugins.test2code.api.*
import com.epam.drill.plugins.test2code.common.api.*
import com.epam.drill.plugins.test2code.coverage.*
import com.epam.kodux.util.*
import java.util.*


class PackageCoverage(val packageName: String) {
    var totalInstruction = 0
    var totalClasses = 0
    var totalMethods = 0
    var classes: List<ClassCoverage> = emptyList()
}

class ClassCoverage(val jvmClassName: String) {
    var totalInstruction = 0
    val methods = mutableMapOf<String, MethodCoverage>()
    var probRangeToInstruction = mutableMapOf<Int, Int>()

    val packageName: String = jvmClassName.substringBeforeLast("/").weakIntern()
    val className: String = jvmClassName.substringAfterLast("/").weakIntern()

    fun method(name: String, desc: String): MethodCoverage {
        val methodName = name.weakIntern()
        return methods[methodName] ?: MethodCoverage(
            methodName,
            desc.weakIntern()
        ).also { methods += (methodName to it) }
    }

    fun toCoverageUnit(probes: Probes): ClassCounter {
        val coverageUnits = methods.values.map {
            it.toCoverageUnit(jvmClassName, probes)
        }
        return ClassCounter(
            path = packageName,
            name = className,
            methods = coverageUnits,
            count = Count(coverageUnits.sumOf { it.count.covered }, totalInstruction)
        )

    }
}

class MethodCoverage(val name: String, val desc: String) {
    var probRangeToInstruction = mutableMapOf<Int, Int>()
    var totalInstruction: Int = 0
    fun toCoverageUnit(ownerClass: String, probes: Probes): MethodCounter {
        var covered = 0
        probRangeToInstruction.forEach { (probeId, v) -> if (probes[probeId]) covered += v }
        return weakPool(
            MethodCounter(
                name = name,
                desc = desc,
                decl = desc,
//            key = "$ownerClass:$name$desc".weakIntern(), todo what is the key?
                Count(covered, totalInstruction)
            )
        )
    }
}

val poolMethodCounter = WeakHashMap<MethodCounter, MethodCounter>()

fun weakPool(methodCounter: MethodCounter): MethodCounter {
    var get = poolMethodCounter[methodCounter]
    if (get == null) {
        poolMethodCounter[methodCounter] = methodCounter
        get = methodCounter
    }
    return get
}

interface BundleProc {
    fun bundle(execClassData: Sequence<ExecClassData>): BundleCounter
    fun fastBundle(analyzedClasses: Map<ExecClassData, ClassCounter?>): BundleCounter
}

internal class JavaBundleProc(val coverContext: CoverContext) : BundleProc {
    private val fullAnalyzedTree: Map<String, Pair<PackageCoverage, Map<String, ClassCoverage>>> =
        coverContext.analyzedClasses.groupBy { it.packageName }
            .map { p2coverUnit ->
                PackageCoverage(p2coverUnit.key).apply {
                    totalInstruction = p2coverUnit.value.sumOf { it.totalInstruction }
                    totalClasses = p2coverUnit.value.size
                    totalMethods = p2coverUnit.value.map { it.methods.values }.flatten().size
                    classes = p2coverUnit.value
                }
            }.associateBy { it.packageName }
            .mapValues {
                it.value to it.value.classes.associateBy { it.jvmClassName }
            }

    override fun bundle(execClassData: Sequence<ExecClassData>): BundleCounter {
        val bundleTree: List<PackageCounter> = execClassData.filter { !it.probes.isEmpty }
            .groupBy { it.className.substringBeforeLast("/").weakIntern() }
            .mapValues {
                it.value.groupBy { it.className }
                    .mapValues { it.value.reduce { acc, execClassData -> acc.merge(execClassData) } }
            }.mapNotNull {
                val packageName = it.key
                fullAnalyzedTree[packageName]?.let { (packageCoverage, classesCoverage) ->
                    val classes = it.value.map { classesCoverage[it.key]!!.toCoverageUnit(it.value.probes) }
                    PackageCounter(
                        name = packageName,
                        classes = classes,
                        count = Count(classes.sumOf { it.count.covered }, packageCoverage.totalInstruction),
                        classCount = Count(classes.size, packageCoverage.totalClasses),
                        methodCount = Count(classes.map { it.methods }.flatten().size, packageCoverage.totalMethods)
                    )
                }
            }
        return bundleTree.toBundle(fullAnalyzedTree.size)
    }

    override fun fastBundle(analyzedClasses: Map<ExecClassData, ClassCounter?>): BundleCounter {
        val bundleTree: List<PackageCounter> =
            analyzedClasses.values.filterNotNull().bundleTree(fullAnalyzedTree.mapValues { it.value.first })
        return bundleTree.toBundle(fullAnalyzedTree.size)
    }
}

internal class NonJavaBundleProc(val coverContext: CoverContext) : BundleProc {
    override fun bundle(execClassData: Sequence<ExecClassData>): BundleCounter {
        return execClassData.asSequence().bundle(coverContext.packageTree)
    }

    override fun fastBundle(analyzedClasses: Map<ExecClassData, ClassCounter?>): BundleCounter {
        return analyzedClasses.keys.asSequence().bundle(coverContext.packageTree)
    }

}


internal class BundleProcessor {

    companion object {
        fun bundleProcessor(coverContext: CoverContext): BundleProc {
            return if (coverContext.agentType == "JAVA")
                JavaBundleProc(coverContext)
            else
                NonJavaBundleProc(coverContext)
        }
    }

}


fun List<PackageCounter>.toBundle(totalCount: Int): BundleCounter {
    return BundleCounter(
        name = "",
        count = Count(sumOf { it.count.covered }, sumOf { it.count.total }),//todo pass total probes,method, classes
        methodCount = Count(
            sumOf { it.methodCount.covered },
            sumOf { it.methodCount.total }
        ),
        classCount = Count(
            sumOf { it.classCount.covered },
            sumOf { it.classCount.total }
        ),
        packageCount = Count(size, totalCount),
        packages = this

    )
}

//todo is it need?
fun Iterable<ClassCounter>.toBundle(fullAnalyzedTree: Map<String, PackageCoverage>): BundleCounter {
    val bundleTree: List<PackageCounter> = bundleTree(fullAnalyzedTree)
    return BundleCounter(
        name = "",
        count = Count(bundleTree.sumOf { it.count.covered }, bundleTree.sumOf { it.count.total }),
        methodCount = Count(
            bundleTree.sumOf { it.methodCount.covered },
            bundleTree.sumOf { it.methodCount.total }
        ),
        classCount = Count(
            bundleTree.sumOf { it.classCount.covered },
            bundleTree.sumOf { it.classCount.total }
        ),
        packageCount = Count(bundleTree.size, fullAnalyzedTree.size),
        packages = bundleTree
    )

}

private fun Iterable<ClassCounter>.bundleTree(packageInfo: Map<String, PackageCoverage>) =
    groupBy(ClassCounter::path).map {
        val classes = it.value
        val packageName = it.key
        val packageCoverage = packageInfo.getValue(packageName)
        PackageCounter(
            name = packageName,
            classes = classes,
            count = Count(classes.sumOf { it.count.covered }, classes.sumOf { it.count.total }),
            classCount = Count(classes.size, packageCoverage.totalClasses),
            methodCount = Count(
                classes.map { it.methods }.flatten().size,
                packageCoverage.totalMethods
            )
        )
    }

