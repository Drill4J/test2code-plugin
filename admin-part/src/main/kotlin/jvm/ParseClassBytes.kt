package com.epam.drill.plugins.test2code.jvm

import com.epam.drill.plugins.test2code.*
import com.epam.drill.plugins.test2code.api.*
import com.epam.drill.plugins.test2code.coverage.*
import com.epam.drill.plugins.test2code.storage.*
import org.jacoco.core.internal.data.*

fun Map<String, ByteArray>.parseClassBytes(agentKey: AgentKey): ClassData = run {
    val probeIds: Map<String, Long> = mapValues { CRC64.classId(it.value) }
    val bundleCoverage = keys.bundle(this, probeIds)
    val sortedPackages = bundleCoverage.packages.asSequence().run {
        mapNotNull { pc ->
            val classes = pc.classes.filter { it.methods.any() }
            if (classes.any()) {
                pc.copy(classes = classes.sortedBy(ClassCounter::name))
            } else null
        }.sortedBy(PackageCounter::name)
    }.toList()
    val classCounters = sortedPackages.asSequence().flatMap {
        it.classes.asSequence()
    }
    val groupedMethods = classCounters.associate { classCounter ->
        val name = classCounter.fullName
        val bytes = getValue(name)
        name to classCounter.parseMethods(bytes).sorted()
    }
    val methods = groupedMethods.flatMap { it.value }
    val packages = sortedPackages.toPackages(groupedMethods)
    PackageTree(
        totalCount = packages.sumOf { it.totalCount },
        totalMethodCount = groupedMethods.values.sumOf { it.count() },
        totalClassCount = packages.sumOf { it.totalClassesCount },
        packages = packages
    ).toClassData(agentKey, methods, probeIds)
}
