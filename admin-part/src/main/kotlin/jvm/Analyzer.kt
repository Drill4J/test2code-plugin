package com.epam.drill.plugins.test2code.jvm

import com.epam.drill.plugins.test2code.*
import com.epam.drill.plugins.test2code.api.*
import com.epam.drill.plugins.test2code.common.api.*
import com.epam.drill.plugins.test2code.coverage.*
import com.epam.drill.plugins.test2code.coverage.bundle
import com.epam.drill.plugins.test2code.util.*
import java.util.*

class MethodCoverage(val name: String, val desc: String) {
    var probRangeToInstruction = mutableMapOf<Int, Int>()
    var totalInstruction: Int = 0

    fun toCoverageUnit(ownerClass: String, probes: BitSet): MethodCounter {
        var covered = 0
        probRangeToInstruction.forEach { (probeId, v) -> if (probes[probeId]) covered += v }
        return MethodCounter(
            name = name,
            desc = desc,
            decl = desc,
            key = "$ownerClass:$name$desc".intr(),
            Count(covered, totalInstruction)
        )
    }
}


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

    val packageName: String = jvmClassName.substringBeforeLast("/").intr()
    val className: String = jvmClassName.substringAfterLast("/").intr()


    fun method(name: String, desc: String): MethodCoverage {
        val methodName = name.intr()
        return methods[methodName] ?: MethodCoverage(methodName, desc.intr()).also { methods += (methodName to it) }
    }

    fun toCoverageUnit(probes: BitSet): ClassCounter {
        val map = methods.values.map { it.toCoverageUnit(jvmClassName, probes) }
        return ClassCounter(
            path = packageName,
            name = className,
            methods = map,
            count = Count(map.map { it.count.covered }.sum(), totalInstruction)
        )

    }
}


interface BundleProc {
    fun bundle(execClassData: Iterable<ExecClassData>): BundleCounter
    fun fastBundle(analyzedClasses: Map<ExecClassData, ClassCounter?>): BundleCounter
}

internal class JavaBundleProc(val coverContext: CoverContext) : BundleProc {
    private val fullAnalyzedTree = coverContext.analyzedClasses.groupBy { it.packageName }
        .map {
            PackageCoverage(it.key).apply {
                totalInstruction = it.value.map { it.totalInstruction }.sum()
                totalClasses = it.value.size
                totalMethods = it.value.map { it.methods.values }.flatten().size
                classes = it.value
            }
        }.associateBy { it.packageName }
        .mapValues {
            it.value to it.value.classes.associateBy { it.jvmClassName }
        }

    override fun bundle(execClassData: Iterable<ExecClassData>): BundleCounter {
        val mapValues = execClassData.filter { !it.probes.isEmpty }
            .groupBy { it.className.substringBeforeLast("/") }
            .mapValues {
                it.value.groupBy { it.className }
                    .mapValues { it.value.reduce { acc, execClassData -> acc.merge(execClassData) } }
            }
        val bundleTree: List<PackageCounter> = mapValues.mapNotNull {
            val packageName = it.key
            fullAnalyzedTree[packageName]?.let { (packageCoverage, classesCoverage) ->
                val classes: List<ClassCounter> =
                    it.value.map { classesCoverage[it.key]!!.toCoverageUnit(it.value.probes) }
                PackageCounter(
                    name = packageName.intr(),
                    classes = classes,
                    count = Count(classes.map { it.count.covered }.sum(), packageCoverage.totalInstruction),
                    classCount = Count(classes.size, packageCoverage.totalClasses),
                    methodCount = Count(classes.map { it.methods }.flatten().size, packageCoverage.totalMethods)
                )
            }
        }

        return bundleTree.toBundle(fullAnalyzedTree.size)
    }

    override fun fastBundle(analyzedClasses: Map<ExecClassData, ClassCounter?>): BundleCounter {
        val bundleTree: List<PackageCounter> = analyzedClasses.values.filterNotNull().groupBy(ClassCounter::path).map {
            val classes = it.value
            val packageName = it.key
            PackageCounter(
                name = packageName,
                classes = classes,
                count = Count(classes.map { it.count.covered }.sum(), classes.map { it.count.total }.sum()),
                classCount = Count(classes.size, fullAnalyzedTree[packageName]!!.first.totalClasses),
                methodCount = Count(
                    classes.map { it.methods }.flatten().size,
                    fullAnalyzedTree[packageName]!!.first.totalMethods
                )
            )
        }
        return bundleTree.toBundle(fullAnalyzedTree.size)
    }
}

internal class NonJavaBundleProc(val coverContext: CoverContext) : BundleProc {
    override fun bundle(execClassData: Iterable<ExecClassData>): BundleCounter {
        return execClassData.asSequence().bundle(coverContext.packageTree)
    }

    override fun fastBundle(analyzedClasses: Map<ExecClassData, ClassCounter?>): BundleCounter {
        return analyzedClasses.keys.asSequence().bundle(coverContext)
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
        count = Count(map { it.count.covered }.sum(), map { it.count.total }.sum()),
        methodCount = Count(
            map { it.methodCount.covered }.sum(),
            map { it.methodCount.total }.sum()
        ),
        classCount = Count(
            map { it.classCount.covered }.sum(),
            map { it.classCount.total }.sum()
        ),
        packageCount = Count(size, totalCount),
        packages = this

    )
}



fun Iterable<ClassCounter>.toBundle(coverContext: Map<String, Pair<PackageCoverage, Map<String, ClassCoverage>>>): BundleCounter {
    val bundleTree: List<PackageCounter> = groupBy(ClassCounter::path).map {
        val classes = it.value
        val packageName = it.key
        PackageCounter(
            name = packageName,
            classes = classes,
            count = Count(classes.map { it.count.covered }.sum(), classes.map { it.count.total }.sum()),
            classCount = Count(classes.size, coverContext[packageName]!!.first.totalClasses),
            methodCount = Count(
                classes.map { it.methods }.flatten().size,
                coverContext[packageName]!!.first.totalMethods
            )
        )
    }
    return BundleCounter(
        name = "",
        count = Count(bundleTree.map { it.count.covered }.sum(), bundleTree.map { it.count.total }.sum()),
        methodCount = Count(
            bundleTree.map { it.methodCount.covered }.sum(),
            bundleTree.map { it.methodCount.total }.sum()
        ),
        classCount = Count(
            bundleTree.map { it.classCount.covered }.sum(),
            bundleTree.map { it.classCount.total }.sum()
        ),
        packageCount = Count(bundleTree.size, coverContext.size),
        packages = bundleTree

    )

}



