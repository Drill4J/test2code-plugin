package com.epam.drill.plugins.test2code

import org.jacoco.core.internal.data.*
import java.util.*
import kotlin.math.*

fun currentTimeMillis() = System.currentTimeMillis()

fun genUuid() = "${UUID.randomUUID()}"

tailrec fun Int.gcd(other: Int): Int = takeIf { other == 0 } ?: other.gcd(rem(other))

fun String.methodName(name: String): String = when (name) {
    "<init>" -> toShortClassName()
    "<clinit>" -> "static ${toShortClassName()}"
    else -> name
}

internal fun String.toShortClassName(): String = substringAfterLast('/')

val String.crc64: String get() = CRC64.classId(toByteArray()).toString(Character.MAX_RADIX)

internal fun String.crc64(): Long = CRC64.classId(toByteArray())

infix fun Number.percentOf(other: Number): Double = when (val dOther = other.toDouble()) {
    0.0 -> 0.0
    else -> toDouble() * 100.0 / dOther
}.round()

internal fun Double.round(): Double = round(this * 10) / 10
