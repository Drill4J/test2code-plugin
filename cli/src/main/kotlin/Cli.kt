package com.epam.drill.plugins.test2code.cli

import com.epam.drill.plugins.test2code.jvm.*
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
        val parsed = classBytes.parseClassBytes(AgentKey("", ""))
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
