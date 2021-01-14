package com.epam.drill.plugins.test2code.util

import kotlinx.serialization.*

inline fun <reified T> BinaryFormat.dump(
    value: T
): ByteArray = encodeToByteArray(serializer(), value)

fun <T> BinaryFormat.dump(
    serializer: SerializationStrategy<T>,
    value: T
): ByteArray = encodeToByteArray(serializer, value)

fun <T> BinaryFormat.load(
    deserializer: DeserializationStrategy<T>,
    bytes: ByteArray
): T = decodeFromByteArray(deserializer, bytes)
