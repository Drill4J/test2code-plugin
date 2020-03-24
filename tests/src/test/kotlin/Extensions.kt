package com.epam.drill.plugins.test2code

import com.epam.drill.admin.common.*
import com.epam.drill.admin.endpoints.*
import com.epam.drill.common.*
import io.ktor.http.cio.websocket.*
import kotlinx.serialization.*

fun String.extractJsonData() = substringAfter("\"data\":").substringBeforeLast("}")

fun Action.stringify() = adminSerDe.stringify(adminSerDe.actionSerializer, this)

@OptIn(ImplicitReflectionSerializer::class)
inline fun <reified T> String.parseJsonData() = serializer<T>().parse(extractJsonData())

fun WsReceiveMessage.toTextFrame(): Frame.Text = Frame.Text(
    WsReceiveMessage.serializer().stringify(this)
)
