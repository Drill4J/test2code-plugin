/**
 * Copyright 2020 EPAM Systems
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.epam.drill.plugins.test2code.coverage

import com.epam.drill.plugins.test2code.*
import com.epam.drill.plugins.test2code.api.*
import com.epam.drill.plugins.test2code.common.api.*
import com.epam.drill.plugins.test2code.util.*
import com.epam.kodux.util.*
import kotlinx.coroutines.*
import kotlin.math.*

internal fun ExecClassData.id(): Long = id ?: className.crc64()

internal fun List<Boolean>.toCount() = Count(count { it }, size)

internal fun <T> List<T>.slice(probeRange: ProbeRange): List<T> = slice(probeRange.first..probeRange.last)

internal fun Count.percentage(): Double = covered percentOf total

internal fun Count?.arrowType(other: Count): ArrowType = this?.run {
    (this.subtraction(other)).first.sign.toArrowType()
} ?: ArrowType.UNCHANGED

internal infix fun Count.subtraction(other: Count): Pair<Long, Long> = takeIf { other.total > 0 }?.run {
    total.gcd(other.total).let { gcd ->
        val (totalLong, otherTotalLong) = total.toLong() to other.total.toLong()
        Pair(
            first = (otherTotalLong / gcd * covered) - (totalLong / gcd * other.covered),
            second = totalLong / gcd * otherTotalLong
        )
    }
} ?: covered.toLong() to total.toLong()

internal fun NamedCounter.hasCoverage(): Boolean = count.covered > 0

internal fun NamedCounter.coverageKey(parent: NamedCounter? = null): CoverageKey = when (this) {
    is MethodCounter -> CoverageKey(
        id = "${parent?.name}.$sign".crc64,
        packageName = (parent as? ClassCounter)?.path?.weakIntern() ?: "",
        className = (parent as? ClassCounter)?.name?.weakIntern() ?: "",
        methodName = name.weakIntern(),
        methodDesc = desc.weakIntern()
    )
    is ClassCounter -> CoverageKey(
        id = "$path.$name".crc64,
        packageName = path.weakIntern(),
        className = name.weakIntern()
    )
    is PackageCounter -> CoverageKey(
        id = name.crc64,
        packageName = name.weakIntern()
    )
    else -> CoverageKey(name.crc64)
}

internal fun BundleCounter.coverageKeys(
    onlyPackages: Boolean = true,
): Sequence<CoverageKey> = packages.asSequence().flatMap { p ->
    sequenceOf(p.coverageKey()) + (p.classes.takeIf { !onlyPackages }?.asSequence()?.flatMap { c ->
        sequenceOf(c.coverageKey()) + c.methods.asSequence().mapNotNull { m ->
            m.takeIf { it.count.covered > 0 }?.coverageKey(c)
        }
    } ?: sequenceOf())
}

internal fun BundleCounter.toCoverDto(
    tree: PackageTree,
) = count.copy(total = tree.totalCount).let { count ->
    CoverDto(
        percentage = count.percentage(),
        methodCount = methodCount.copy(total = tree.totalMethodCount).toDto(),
        count = count.toDto()
    )
}

internal fun Map<String, CoverMethod>.toCoverMap(
    bundle: BundleCounter,
    onlyCovered: Boolean
): Map<String, CoverMethod> {
    if (bundle == BundleCounter.empty)
        return this
    return bundle.packages.flatMap { it.classes }.flatMap { c ->
        c.methods.mapNotNull { m ->
            m.takeIf { !onlyCovered || it.count.covered > 0 }?.let {
                m.key to get(m.key)!!.copy(
                    count = it.count,
                    coverageRate = it.count.coverageRate()
                )
            }

        }
    }.toMap()
}
//todo remove it
internal suspend fun List<Method>.toCoverMap(
    bundle: BundleCounter,
    onlyCovered: Boolean,
): Map<Method, CoverMethod> = bundle.packages.asSequence().let { packages ->
    val map = packages.flatMap { it.classes.asSequence() }.flatMap { c ->
        c.methods.asSequence().map { m -> Pair(c.fullName, m.sign) to m }
    }.toMap()
    val subCollectionSize = (size / availableProcessors).takeIf { it > 0 } ?: 1
    chunked(subCollectionSize).map { subList ->
        AsyncJobDispatcher.async {
            subList.mapNotNull { method ->
                val covered = method.toCovered(map[method.ownerClass to method.signature()])
                covered.takeIf { !onlyCovered || it.count.covered > 0 }?.let { method to it }
            }
        }
    }.flatMap { it.await() }.toMap()
}
internal fun BundleCounter.coveredMethods(
    methods: Iterable<Method>,
): Map<Method, Count> = packages.asSequence().takeIf { p ->
    p.any { it.classes.any() }
}?.run {
    toCoveredMethods(
        { methods.groupBy(Method::ownerClass) },
        { methods.toPackageSet() }
    ).toMap()
}.orEmpty()

internal fun Sequence<PackageCounter>.toCoveredMethods(
    methodMapPrv: () -> Map<String, List<Method>>,
    packageSetPrv: () -> Set<String>,
): Sequence<Pair<Method, Count>> = takeIf { it.any() }?.run {
    val packageSet = packageSetPrv()
    filter { it.name in packageSet && it.hasCoverage() }.run {
        val methodMap = methodMapPrv()
        flatMap {
            it.classes.asSequence().filter(NamedCounter::hasCoverage)
        }.mapNotNull { c ->
            methodMap[c.fullName]?.run {
                val covered: Map<String, MethodCounter> = c.methods.asSequence()
                    .filter(NamedCounter::hasCoverage)
                    .associateBy(MethodCounter::sign)
                mapNotNull { m -> covered[m.signature]?.let { m to it.count } }.asSequence()
            }
        }.flatten()
    }
}.orEmpty()

internal fun Iterable<Method>.toPackageSet(): Set<String> = takeIf { it.any() }?.run {
    mapTo(mutableSetOf()) { method ->
        method.ownerClass.takeIf { '/' in it }?.substringBeforeLast('/').orEmpty().weakIntern()
    }
}.orEmpty()

internal fun Method.toCovered(count: Count?) = CoverMethod(
    ownerClass = ownerClass.weakIntern(),
    name = ownerClass.methodName(name),
    desc = desc.weakIntern(),//.takeIf { "):" in it } ?: declaration(desc), //TODO js methods //Regex has a big impact on performance
    hash = hash.weakIntern(),
    count = count ?: zeroCount,
    coverageRate = count?.coverageRate() ?: CoverageRate.MISSED
)

internal fun Method.toCovered(counter: MethodCounter? = null): CoverMethod = toCovered(counter?.count)

internal fun String.typedTest(type: String) = TypedTest(
    type = type.weakIntern(),
    name = urlDecode().weakIntern()
)

internal fun TypedTest.id() = "$name:$type".weakIntern()

private fun Int.toArrowType(): ArrowType? = when (this) {
    in Int.MIN_VALUE..-1 -> ArrowType.INCREASE
    in 1..Int.MAX_VALUE -> ArrowType.DECREASE
    else -> null
}

private fun Method.signature() = "$name$desc".weakIntern()

//TODO remove
internal fun Count.coverageRate() = when (covered) {
    0 -> CoverageRate.MISSED
    in 1 until total -> CoverageRate.PARTLY
    else -> CoverageRate.FULL
}
