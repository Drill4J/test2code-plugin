/**
 * Copyright 2020 - 2022 EPAM Systems
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
package com.epam.drill.plugins.test2code.checksum

// That file was taken from test2code-admin
fun signature(
    clazz: String,
    method: String,
    desc: String
): String = "${methodName(method, clazz)}$desc"

fun methodName(method: String, fullClassname: String): String = when (method) {
    "<init>" -> classname(fullClassname)
    "<clinit>" -> "static ${classname(fullClassname)}"
    else -> method
}

fun classname(
    fullClassname: String
): String = fullClassname.substringAfterLast('/')
