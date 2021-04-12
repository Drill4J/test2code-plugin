package com.epam.drill.plugins.test2code.common.api

import kotlinx.serialization.*
import kotlinx.serialization.builtins.*
import kotlinx.serialization.descriptors.*
import kotlinx.serialization.encoding.*

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

fun Probes.toBooleanArray(): BooleanArray {
    return BooleanArray(length()) { //todo optimizze!
        get(it)
    }
}

fun BooleanArray.toBitSet(): Probes {
    return Probes(size).apply {
        forEachIndexed { index, b ->
            set(index, b)
        }
    }
}

fun List<Boolean>.toBitSet(): Probes {
    return Probes(size).apply {
        forEachIndexed { index, b ->
            set(index, b)
        }
    }
}


