package com.epam.drill.plugins.test2code.util

import com.epam.drill.plugins.test2code.*
import kotlin.time.*

val logger = logger {}

inline fun <T> trackTime(tag: String = "", debug: Boolean = true, block: () -> T) =
    measureTimedValue { block() }.apply {
        when {
            duration.inSeconds > 1 -> {
                logger.warn { "[$tag] took: $duration" }
            }
            duration.inSeconds > 30 -> {
                logger.error { "[$tag] took: $duration" }
            }
            else -> if (debug) logger.debug { "[$tag] took: $duration" } else logger.trace { "[$tag] took: $duration" }
        }
    }.value
