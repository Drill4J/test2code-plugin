package com.epam.drill.plugins.test2code.common

import java.util.*

fun genUuid(): String = UUID.randomUUID().toString()

fun currentTimeMillis(): Long = System.currentTimeMillis()