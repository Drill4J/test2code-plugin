package com.epam.drill.plugins.test2code

import java.util.*

fun currentTimeMillis() = System.currentTimeMillis()

fun genUuid() = "${UUID.randomUUID()}"

tailrec fun Int.gcd(other: Int): Int = takeIf { other == 0 } ?: other.gcd(rem(other))
