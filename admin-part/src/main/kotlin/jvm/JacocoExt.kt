package com.epam.drill.plugins.test2code.jvm

import com.epam.drill.plugins.test2code.api.*
import com.epam.drill.plugins.test2code.common.api.*
import com.epam.drill.plugins.test2code.coverage.*
import com.epam.drill.plugins.test2code.util.*
import mu.*
import org.jacoco.core.analysis.*
import org.jacoco.core.data.*

private val logger = KotlinLogging.logger {}

internal typealias ClassBytes = Map<String, ByteArray>

internal fun Sequence<ExecClassData>.bundle(
    probeIds: Map<String, Long>,
    classBytes: ClassBytes
): BundleCounter = bundle(probeIds) { analyzer ->
    contents.forEach { execData ->
        classBytes[execData.name]?.let { classesBytes ->
            analyzer.analyzeClass(classesBytes, execData.name)
        } ?: println("WARN No class data for ${execData.name}, id=${execData.id}")
    }
}.toCounter()

internal fun Iterable<String>.bundle(
    classBytes: Map<String, ByteArray>,
    probeIds: Map<String, Long>
): BundleCounter = emptySequence<ExecClassData>().bundle(probeIds) { analyzer ->
    forEach { name -> analyzer.analyzeClass(classBytes.getValue(name), name) }
}.toCounter()

private fun Sequence<ExecClassData>.bundle(
    probeIds: Map<String, Long>,
    analyze: ExecutionDataStore.(Analyzer) -> Unit
): IBundleCoverage = CoverageBuilder().also { coverageBuilder ->
    val dataStore = execDataStore(probeIds)
    val analyzer = Analyzer(dataStore, coverageBuilder)
    dataStore.analyze(analyzer)
}.getBundle("")


internal fun Sequence<ExecClassData>.execDataStore(
    probeIds: Map<String, Long>
): ExecutionDataStore = mapNotNull {
    it.toExecutionData(probeIds)
}.fold(ExecutionDataStore()) { store, execData ->
    store.apply {
        runCatching { put(execData) }.onFailure { e ->
            logger.error(e) {
                "Error adding ${execData}, probes=(${execData.probes?.size})${execData.probes?.contentToString()}"
            }
        }
    }
}

internal fun IBundleCoverage.toCounter() = BundleCounter(
    name = "",
    count = instructionCounter.toCount(),
    methodCount = methodCounter.toCount(),
    classCount = classCounter.toCount(),
    packageCount = packages.run { Count(count { it.classCounter.coveredCount > 0 }, count()) },
    packages = packages.mapNotNull { p ->
        val classesWithMethods = p.classes.filter { c ->
            c.methods.any().also {
                if (!it) {
                    logger.warn { "Class without methods - ${c.name}." }
                }
            }
        }
        if (classesWithMethods.any()) {
            PackageCounter(
                name = p.name,
                count = p.instructionCounter.toCount(),
                classCount = p.classCounter.toCount(),
                methodCount = p.methodCounter.toCount(),
                classes = classesWithMethods.map { c ->
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
        } else null
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

private fun ExecClassData.toExecutionData(probeIds: Map<String, Long>): ExecutionData? = probeIds[className]?.let {
    ExecutionData(it, className, probes.toBooleanArray())
}
