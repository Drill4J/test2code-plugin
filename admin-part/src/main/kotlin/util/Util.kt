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
package com.epam.drill.plugins.test2code.util

import kotlinx.coroutines.*
import org.jacoco.core.internal.data.*
import java.net.*
import java.util.*
import java.util.concurrent.*

fun currentTimeMillis() = System.currentTimeMillis()

fun genUuid() = "${UUID.randomUUID()}"

internal val availableProcessors = Runtime.getRuntime().availableProcessors()

internal val allAvailableProcessDispatcher = Executors.newFixedThreadPool(availableProcessors).asCoroutineDispatcher()

tailrec fun Int.gcd(other: Int): Int = takeIf { other == 0 } ?: other.gcd(rem(other))

fun String.methodName(name: String): String = when (name) {
    "<init>" -> toShortClassName().intr()
    "<clinit>" -> "static ${toShortClassName()}".intr()
    else -> name.intr()
}

internal fun String.urlDecode(): String = takeIf { '%' in it }?.run {
    runCatching { URLDecoder.decode(this, "UTF-8") }.getOrNull()
} ?: this

internal fun String.toShortClassName(): String = substringAfterLast('/').intr()

val String.crc64: String get() = CRC64.classId(toByteArray()).toString(Character.MAX_RADIX).intr()

internal fun String.crc64(): Long = CRC64.classId(toByteArray())

internal fun ByteArray.crc64(): Long = CRC64.classId(this)

infix fun Number.percentOf(other: Number): Double = when (val dOther = other.toDouble()) {
    0.0 -> 0.0
    else -> toDouble() * 100.0 / dOther
}
