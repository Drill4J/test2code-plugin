package com.epam.drill.plugins.test2code

import mu.*

private val packagePrefix: String = ::logger::class.qualifiedName?.run {
    substringBeforeLast('.').substringBeforeLast('.')
}.orEmpty()

internal fun logger(block: () -> Unit): KLogger = run {
    val name = block::class.qualifiedName?.run {
        if ('$' in this) substringBefore('$') else this
    }?.toLoggerName().orEmpty()
    KotlinLogging.logger(name)
}

internal fun Any.logger(vararg fields: String): KLogger = run {
    val name = this::class.qualifiedName?.toLoggerName().orEmpty()
    val suffix = fields.takeIf { it.any() }?.joinToString(prefix = "(", postfix = ")").orEmpty()
    KotlinLogging.logger("$name$suffix")
}

private fun String.toLoggerName(): String = removePrefix(packagePrefix).run {
    "plugins/${replace('.', '/')}"
}
