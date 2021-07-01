package com.epam.drill.plugins.test2code.common.api

import kotlinx.serialization.*
import kotlinx.serialization.builtins.*
import kotlinx.serialization.descriptors.*
import kotlinx.serialization.encoding.*

expect class Probes(size: Int) {
    fun length(): Int
    fun get(ind: Int): Boolean
    fun set(ind: Int, value: Boolean)
}

object BitSetSerializer : KSerializer<Probes> {

    override fun serialize(encoder: Encoder, value: Probes) {
        encoder.encodeSerializableValue(BooleanArraySerializer(), value.toBooleanArray())
    }

    override fun deserialize(decoder: Decoder): Probes {
        val decodeSerializableValue = decoder.decodeSerializableValue(BooleanArraySerializer())
        return decodeSerializableValue.toBitSet()
    }

    override val descriptor: SerialDescriptor
        get() = buildClassSerialDescriptor("BitSet")
}

/**
 * for all BitSet it adds the true into last index.
 * So it needs to remove the last element before converting
 */
fun Probes.toBooleanArray(): BooleanArray {
    return BooleanArray(length() - 1) {// bitset magic
        get(it)
    }
}

fun Probes.toList(): List<Boolean> {
    return toBooleanArray().toList()
}

fun BooleanArray.toBitSet(): Probes {
    val finalSize = size + 1
    return Probes(finalSize).apply {
        forEachIndexed { index, b ->
            set(index, b)
        }
        set(size, true) // bitset magic
    }
}

fun List<Boolean>.toBitSet(): Probes {
    val finalSize = size + 1
    return Probes(finalSize).apply {
        forEachIndexed { index, b ->
            set(index, b)
        }
        set(size, true) // bitset magic
    }
}

fun probesOf(vararg elements: Boolean): Probes {
    return elements.toBitSet()
}

