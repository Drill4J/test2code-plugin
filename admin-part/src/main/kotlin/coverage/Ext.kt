package com.epam.drill.plugins.test2code.coverage

import com.epam.drill.common.*
import com.epam.drill.plugins.test2code.*
import com.epam.drill.plugins.test2code.api.*
import kotlin.math.*

val CoverageKey.isMethod get() = methodName.any()

fun List<Boolean>.toCount() = Count(count { it }, size)

internal fun <T> List<T>.slice(probeRange: ProbeRange): List<T> = slice(probeRange.first..probeRange.last)

internal fun Count.percentage(): Double = covered percentOf total

internal fun Count.arrowType(other: Count): ArrowType? = (this - other).first.sign.toArrowType()

internal fun Iterable<Count>.sum() = Count(
    covered = sumBy(Count::covered),
    total = sumBy(Count::total)
).takeIf { it != zeroCount } ?: zeroCount

internal operator fun Count.minus(other: Count): Pair<Long, Long> = takeIf { other.total > 0 }?.run {
    total.gcd(other.total).let { gcd ->
        val (totalLong, otherTotalLong) = total.toLong() to other.total.toLong()
        Pair(
            first = (otherTotalLong / gcd * covered) - (totalLong / gcd * other.covered),
            second = totalLong / gcd * otherTotalLong
        )
    }
} ?: covered.toLong() to total.toLong()

internal fun NamedCounter.coverageKey(parent: NamedCounter? = null): CoverageKey = when (this) {
    is MethodCounter -> CoverageKey(
        id = "${parent?.name}.$name$desc".crc64,
        packageName = (parent as? ClassCounter)?.path ?: "",
        className = (parent as? ClassCounter)?.fullName ?: "",
        methodName = name,
        methodDesc = desc
    )
    is ClassCounter -> CoverageKey(
        id = "$path.$name".crc64,
        packageName = path,
        className = name
    )
    is PackageCounter -> CoverageKey(
        id = name.crc64,
        packageName = name
    )
    else -> CoverageKey(name.crc64)
}

internal fun BundleCounter.coverageKeys(): Sequence<CoverageKey> = packages.asSequence().flatMap { p ->
    sequenceOf(p.coverageKey()) + p.classes.asSequence().flatMap { c ->
        sequenceOf(c.coverageKey()) + c.methods.asSequence().mapNotNull { m ->
            m.takeIf { it.count.covered > 0 }?.coverageKey(c)
        }
    }
}

internal fun Iterable<Method>.toCoverMap(
    bundle: BundleCounter,
    onlyCovered: Boolean
): Map<Method, CoverMethod> = bundle.packages.asSequence().let { packages ->
    val map = packages.flatMap { it.classes.asSequence() }.flatMap { c ->
        c.methods.asSequence().map { m -> Pair(c.fullName, m.sign) to m }
    }.toMap()
    mapNotNull { m ->
        val covered = m.toCovered(map[m.ownerClass to m.sign])
        covered.takeIf { !onlyCovered || it.count.covered > 0 }?.let { m to it }
    }.toMap()
}

internal fun MethodCounter.coverageRate() = when (count.covered) {
    0 -> CoverageRate.MISSED
    in 1 until count.total -> CoverageRate.PARTLY
    else -> CoverageRate.FULL
}

private fun Int.toArrowType(): ArrowType? = when (this) {
    in Int.MIN_VALUE..-1 -> ArrowType.INCREASE
    in 1..Int.MAX_VALUE -> ArrowType.DECREASE
    else -> null
}

internal fun Method.toCovered(counter: MethodCounter? = null) = CoverMethod(
    ownerClass = ownerClass,
    name = ownerClass.methodName(name),
    desc = desc.takeIf { "):" in it } ?: declaration(desc), //TODO js methods
    hash = hash,
    count = counter?.count ?: zeroCount,
    coverageRate = counter?.coverageRate() ?: CoverageRate.MISSED
)

private val Method.sign get() = "$name$desc"
