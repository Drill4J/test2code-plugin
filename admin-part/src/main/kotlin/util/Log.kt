package com.epam.drill.plugins.test2code.util

import com.epam.drill.plugins.test2code.*
import com.epam.drill.plugins.test2code.logger
import kotlin.time.*

val logger = logger {}

inline fun <T> trackTime(tag: String = "", block: () -> T) =
    measureTimedValue { block() }.apply {
        when {
            duration.inSeconds > 1 -> {
                logger.warn { "[$tag] took: $duration" }
            }
            duration.inSeconds > 30 -> {
                logger.error { "[$tag] took: $duration" }
            }
            else -> logger.debug { "[$tag] took: $duration" }
        }
    }.value
