package com.epam.drill.plugins.test2code

import com.epam.drill.plugins.test2code.api.*
import com.epam.drill.plugins.test2code.common.api.*
import com.epam.drill.plugins.test2code.coverage.*
import org.jacoco.core.analysis.*
import org.jacoco.core.data.*
import org.jacoco.core.internal.data.*

internal fun Sequence<ExecClassData>.bundle(
    classesBytes: ClassesBytes
): BundleCounter = bundle { analyzer ->
    contents.forEach { execData ->
        classesBytes[execData.name]?.let { classesBytes ->
            analyzer.analyzeClass(classesBytes, execData.name)
        } ?: println("WARN No class data for ${execData.name}, id=${execData.id}")
    }
}.toCounter()

internal fun ClassesBytes.bundle(
    data: Sequence<ExecClassData> = emptySequence()
): BundleCounter = data.bundle { analyzer ->
    forEach { (name, bytes) -> analyzer.analyzeClass(bytes, name) }
}.toCounter()

private fun Sequence<ExecClassData>.bundle(
    analyze: ExecutionDataStore.(Analyzer) -> Unit
): IBundleCoverage = CoverageBuilder().also { coverageBuilder ->
    val dataStore = execDataStore()
    val analyzer = Analyzer(dataStore, coverageBuilder)
    dataStore.analyze(analyzer)
}.getBundle("")


internal fun Sequence<ExecClassData>.execDataStore(): ExecutionDataStore = map(ExecClassData::toExecutionData)
    .fold(ExecutionDataStore()) { store, execData ->
        store.apply { put(execData) }
    }

internal fun IBundleCoverage.toCounter() = BundleCounter(
    name = "",
    count = instructionCounter.toCount(),
    methodCount = methodCounter.toCount(),
    packages = packages.map { p ->
        PackageCounter(
            name = p.name,
            count = p.instructionCounter.toCount(),
            classCount = p.classCounter.toCount(),
            methodCount = p.methodCounter.toCount(),
            classes = p.classes.map { c ->
                ClassCounter(
                    path = p.name,
                    name = c.name.toShortClassName(),
                    count = c.instructionCounter.toCount(),
                    methods = c.methods.map { m ->
                        MethodCounter(
                            name = m.name,
                            desc = m.desc,
                            decl = declaration(m.desc),
                            count = m.instructionCounter.toCount()
                        )
                    }
                )
            }
        )
    }
)

fun ICoverageNode.coverage(total: Int = instructionCounter.totalCount): Double =
    instructionCounter.coveredCount percentOf total

internal fun ICounter.toCount(total: Int = totalCount) = Count(covered = coveredCount, total = total)

/**
 * Converts ASM method description to declaration in java style with kotlin style of return type.
 *
 * Examples:
 * - ()V -> (): void
 * - (IZ)V -> (int, boolean): void
 * - (ILjava/lang/String;)V -> (int, String): void
 * - ([Ljava/lang/String;IJ)V -> (String[], int, long): void
 * - ([[IJLjava/lang/String;)Ljava/lang/String; -> (int[][], long, String): String
 * @param desc
 * ASM method description
 * @return declaration in java style with kotlin style of return type
 *
 */
fun declaration(desc: String): String {
    return """\((.*)\)(.+)""".toRegex().matchEntire(desc)?.run {
        val argDesc = groupValues[1]
        val returnTypeDesc = groupValues[2]
        val argTypes = parseDescTypes(argDesc).joinToString(separator = ", ")
        val returnType = parseDescTypes(returnTypeDesc).first()
        "($argTypes): $returnType"
    } ?: ""
}

fun parseDescTypes(argDesc: String): List<String> {
    val types = mutableListOf<String>()
    val descItr = argDesc.iterator()
    while (descItr.hasNext()) {
        val char = descItr.nextChar()
        val arg = parseDescType(char, descItr)
        types.add(arg)
    }
    return types
}

fun parseDescType(char: Char, charIterator: CharIterator): String = when (char) {
    'V' -> "void"
    'J' -> "long"
    'Z' -> "boolean"
    'I' -> "int"
    'F' -> "float"
    'B' -> "byte"
    'D' -> "double"
    'S' -> "short"
    'C' -> "char"
    '[' -> "${parseDescType(charIterator.nextChar(), charIterator)}[]"
    'L' -> {
        val objectDescSeq = charIterator.asSequence().takeWhile { it != ';' }
        val objectDesc = objectDescSeq.fold(StringBuilder()) { sBuilder, c -> sBuilder.append(c) }.toString()
        objectDesc.substringAfterLast("/")
    }
    else -> "!Error"
}

private fun ExecClassData.toExecutionData() = ExecutionData(id, className, probes.toBooleanArray())
