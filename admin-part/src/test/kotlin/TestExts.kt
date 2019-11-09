package com.epam.drill.plugins.coverage

fun Class<*>.readBytes(): ByteArray = getResourceAsStream(
    "/${name.replace('.', '/')}.class"
).readBytes()

internal val Class<*>.path get() = this.name.replace('.', '/')