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
package com.epam.drill.plugins.test2code

import com.epam.drill.admin.common.*
import com.epam.drill.admin.common.serialization.*
import com.epam.drill.plugins.test2code.api.*
import com.epam.drill.plugins.test2code.common.api.*
import io.ktor.application.*
import io.ktor.http.*
import io.ktor.http.cio.websocket.*
import io.ktor.locations.*

fun String.extractJsonData() = substringAfter("\"data\":").substringBeforeLast("}")

fun Action.stringify() = Action.serializer().stringify(this)

fun AgentAction.stringify() = AgentAction.serializer().stringify(this)

inline fun <reified T: AgentAction> String.parseJsonData() = AgentAction.serializer().parse(extractJsonData()) as T

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
