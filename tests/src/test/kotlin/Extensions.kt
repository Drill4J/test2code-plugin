package com.epam.drill.plugins.test2code

import com.epam.drill.admin.common.*
import com.epam.drill.common.*
import com.epam.drill.plugins.test2code.api.*
import com.epam.drill.plugins.test2code.common.api.*
import io.ktor.application.*
import io.ktor.http.*
import io.ktor.http.cio.websocket.*
import io.ktor.locations.*
import kotlinx.serialization.*

fun String.extractJsonData() = substringAfter("\"data\":").substringBeforeLast("}")

fun Action.stringify() = Action.serializer().stringify(this)

fun AgentAction.stringify() = AgentAction.serializer().stringify(this)

@OptIn(ImplicitReflectionSerializer::class)
inline fun <reified T> String.parseJsonData() = serializer<T>().parse(extractJsonData())

fun WsReceiveMessage.toTextFrame(): Frame.Text = Frame.Text(
    WsReceiveMessage.serializer().stringify(this)
)

fun Application.resolve(destination: String): Any {
    val p = "\\{(.*)}".toRegex()
    val urlTokens = destination.split("/")

    val filter = pathToCallBackMapping.filter { it.key.count { c -> c == '/' } + 1 == urlTokens.size }.filter {
        var matche = true
        it.key.split("/").forEachIndexed { x, y ->
            if (!(y == urlTokens[x] || y.startsWith("{"))) {
                matche = false
            }
        }
        matche
    }
    val suitableRout = filter.entries.first()

    val parameters = suitableRout.run {
        val mutableMapOf = mutableMapOf<String, String>()
        key.split("/").forEachIndexed { x, y ->
            if (y != urlTokens[x] && (p.matches(y))) {
                mutableMapOf[p.find(y)!!.groupValues[1]] = urlTokens[x]
            }
        }
        val map = mutableMapOf.map { Pair(it.key, listOf(it.value)) }
        parametersOf(* map.toTypedArray())
    }
    return feature(Locations).resolve(suitableRout.value, parameters)

}
