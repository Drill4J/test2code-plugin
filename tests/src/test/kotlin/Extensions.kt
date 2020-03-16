package com.epam.drill.plugins.test2code

import com.epam.drill.common.*
import kotlinx.serialization.*

fun String.extractJsonData() = substringAfter("\"data\":").substringBeforeLast("}")

fun Action.stringify() = adminSerDe.stringify(adminSerDe.actionSerializer, this)

@UseExperimental(ImplicitReflectionSerializer::class)
inline fun <reified T> String.parseJsonData() = serializer<T>().parse(extractJsonData())
