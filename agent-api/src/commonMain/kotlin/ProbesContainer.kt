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
        val booleanArray = value.toBooleanArray()
        println("Serialize probes with size ${value.length()}. Result size: ${booleanArray.size}")
        encoder.encodeSerializableValue(BooleanArraySerializer(), booleanArray)
    }

    override fun deserialize(decoder: Decoder): Probes {
        val decodeSerializableValue = decoder.decodeSerializableValue(BooleanArraySerializer())
        val bitSet = decodeSerializableValue.toBitSet()
        println("Deserialize boolean array with size ${decodeSerializableValue.size}. Result size: ${bitSet.length()}")
        return bitSet
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

