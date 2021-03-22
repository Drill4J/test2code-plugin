/**
 * Copyright 2020 EPAM Systems
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.epam.drill.plugins.test2code.util

import kotlinx.serialization.*
import kotlinx.serialization.builtins.*
import kotlinx.serialization.descriptors.*
import kotlinx.serialization.encoding.*
import kotlinx.serialization.internal.*


class StringInternDeserializationStrategy<T>(private val deserializationStrategy: DeserializationStrategy<T>) :
    DeserializationStrategy<T> by deserializationStrategy {
    override fun deserialize(decoder: Decoder): T {
        return deserializationStrategy.deserialize(DecodeAdapter(decoder))
    }
}

class StringInternCollectionDeserializationStrategy<T>(private val deserializationStrategy: AbstractCollectionSerializer<*, T, *>) :
    DeserializationStrategy<T> by deserializationStrategy {

    override fun deserialize(decoder: Decoder): T {
        return deserializationStrategy.deserialize(DecodeAdapter(decoder))
    }
}

class StringInternMapDeserializationStrategy<T>(
    private val deserializationStrategy: MapLikeSerializer<*, *, T, *>,
) : DeserializationStrategy<T> by deserializationStrategy {

    override val descriptor: SerialDescriptor
        get() = deserializationStrategy.descriptor

    override fun deserialize(decoder: Decoder): T {
        // deserializationStrategy.keySerializer
//        deserializationStrategy.keySerializer.descriptor
//        deserializationStrategy.
        return deserializationStrategy.deserialize(decoder)
    }
}

internal class StringInternDecoder(private val decoder: CompositeDecoder) : CompositeDecoder by decoder {
    override fun decodeStringElement(descriptor: SerialDescriptor, index: Int): String {
        return decoder.decodeStringElement(descriptor, index).run {
            takeIf { descriptor.getElementAnnotations(index).any { it is StringIntern } }?.intern() ?: this
        }
    }

    override fun <T> decodeSerializableElement(
        descriptor: SerialDescriptor,
        index: Int,
        deserializer: DeserializationStrategy<T>,
        previousValue: T?
    ): T {
        @Suppress("UNCHECKED_CAST")
        val deserializationStrategy: DeserializationStrategy<T> = when (deserializer) {
            is MapLikeSerializer<*, *, *, *> -> StringInternDeserializationStrategy(deserializer) as DeserializationStrategy<T>
            is AbstractCollectionSerializer<*, *, *> -> StringInternDeserializationStrategy(deserializer) as DeserializationStrategy<T>
            else -> deserializer.takeIf {
                deserializer.descriptor == ByteArraySerializer().descriptor
            } ?: StringInternDeserializationStrategy(deserializer)
        }
        return decoder.decodeSerializableElement(
            descriptor,
            index,
            deserializationStrategy,
            previousValue
        )
    }
}

internal class DecodeAdapter(private val decoder: Decoder) : Decoder by decoder {
    override fun beginStructure(descriptor: SerialDescriptor): CompositeDecoder {
        return StringInternDecoder(decoder.beginStructure(descriptor))
    }
}
