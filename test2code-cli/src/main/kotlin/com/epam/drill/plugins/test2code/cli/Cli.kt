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
package com.epam.drill.plugins.test2code.cli

import com.epam.drill.plugins.test2code.ClassData
import com.epam.drill.plugins.test2code.storage.*
import com.github.ajalt.clikt.core.*
import com.github.ajalt.clikt.parameters.options.*
import java.io.*

fun main(args: Array<String>) = ClassParse().main(args)

private class ClassParse : CliktCommand() {
    private val classpath: List<String>? by option(help = "Classpath (comma separated)").split(",")
    private val packages: List<String> by option(help = "Project packages (comma separated)")
        .split(",")
        .default(emptyList())
    private val output: String? by option(help = "Output file, if not specified stdout is used")

    override fun run() {
        val classBytes = mutableMapOf<String, ByteArray>()
        val files = classpath?.map { File(it).toURI().toURL() } ?: emptyList()
        files.forEach { file ->
            file.scan(packages) { classname, bytes -> classBytes[classname] = bytes }
        }
//        val parsed = classBytes.parseClassBytes(AgentKey("", ""))
        //todo temporarily unsupported
        val parsed = ClassData(AgentKey("", ""))
        val outputValue = """
             classCount: ${parsed.packageTree.totalClassCount}
             methodCount: ${parsed.packageTree.totalMethodCount}
        """.trimIndent()
        output?.let {
            File(it).bufferedWriter().use { writer ->
                writer.append(outputValue)
            }
        } ?: echo(outputValue)
    }
}
