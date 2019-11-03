package com.epam.drill.plugins.coverage

internal fun Class<*>.readBytes(): ByteArray = getResourceAsStream(
    "/${name.replace('.', '/')}.class"
).readBytes()