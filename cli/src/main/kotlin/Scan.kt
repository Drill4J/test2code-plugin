package com.epam.drill.plugins.test2code.cli

import java.io.*
import java.net.*
import java.util.jar.*

//TODO code from java-agent

fun URL.scan(
    filters: List<String>,
    block: (String, ByteArray) -> Unit
): Unit? = runCatching { File(toURI()) }.getOrNull()?.takeIf { it.exists() }?.let { file ->
    if (file.isDirectory) {
        file.scan(filters, block)
    } else JarInputStream(file.inputStream()).use {
        it.scan(filters, block)
        it.manifest?.classPath(this)?.forEach { url ->
            url.scan(filters, block)
        }
    }
}

private fun Manifest.classPath(jarUrl: URL): Set<URL>? = mainAttributes.run {
    getValue(Attributes.Name.CLASS_PATH.toString())?.split(" ")
}?.mapNotNullTo(mutableSetOf()) { path ->
    runCatching { URL(jarUrl, path) }.getOrNull()?.takeIf { it.protocol == "file" }
}

internal fun JarInputStream.scan(
    packages: List<String>,
    block: (String, ByteArray) -> Unit
): Unit = forEachFile { entry: JarEntry ->
    val name = entry.name
    when (name.substringAfterLast('.')) {
        "class" -> run {
            val source = name.toClassName()
            if (source.isAllowed(packages)) {
                val bytes = readBytes()
                block(source, bytes)
            }
        }
        "jar" -> JarInputStream(ByteArrayInputStream(readBytes())).scan(packages, block)
    }
}

private tailrec fun JarInputStream.forEachFile(block: JarInputStream.(JarEntry) -> Unit) {
    val entry = nextJarEntry ?: return
    if (!entry.isDirectory) {
        block(entry)
    }
    forEachFile(block)
}

internal fun File.scan(
    packages: List<String>,
    block: (String, ByteArray) -> Unit
) = walkTopDown().filter { it.isFile && it.extension == "class" }.forEach { f ->
    val source = f.path.toClassName()
    if (source.isAllowed(packages)) {
        val bytes = f.readBytes()
        block(source, bytes)
    }
}

private fun String.isAllowed(filters: List<String>): Boolean = run {
    '$' !in this && matches(filters)
}

private fun String.toClassName() = replace(File.separatorChar, '/')
    .substringAfterLast("classes/")
    .removeSuffix(".class")

fun String.matches(
    filters: Iterable<String>, thisOffset: Int = 0
): Boolean = filters.any {
    regionMatches(thisOffset, it, 0, it.length)
} && filters.none {
    it.startsWith('!') && regionMatches(thisOffset, it, 1, it.length - 1)
}
