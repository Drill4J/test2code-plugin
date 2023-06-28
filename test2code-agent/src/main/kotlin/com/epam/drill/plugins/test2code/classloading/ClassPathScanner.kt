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
package com.epam.drill.plugins.test2code.classloading

import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream
import java.net.URI
import java.util.jar.Attributes
import java.util.jar.JarEntry
import java.util.jar.JarInputStream
import org.objectweb.asm.ClassReader

private const val PREFIX_SPRING_BOOT = "BOOT-INF/classes/"
private const val PREFIX_WEB_APP = "WEB-INF/classes/"
private const val PACKAGE_DRILL = "com/epam/drill"
private const val PACKAGE_TTL = "com/alibaba/ttl"
private const val JAR_BUFFER_SIZE = 256 * 1024

class ClassPathScanner(
    private val packagePrefixes: List<String>,
    private val classesBufferSize: Int = 50,
    private val transfer: (Set<ClassSource>) -> Unit
) {

    private val scannedJarFiles = mutableSetOf<String>()
    private val scannedClasses = mutableSetOf<String>()
    private val scannedBuffer = mutableSetOf<ClassSource>()

    fun transferBuffer() = scannedBuffer.takeIf(Collection<ClassSource>::isNotEmpty)?.let(transfer)

    fun scanURI(uri: URI) = uri.let(::File).takeIf(File::exists)?.let(::scanFile) ?: 0

    fun scanFile(file: File) = file.takeIf(File::isDirectory)?.let(::scanDirectory) ?: scanJarFile(file)

    private fun scanDirectory(file: File) = file.run {
        val isClassFile: (File) -> Boolean = { it.isFile && it.extension == "class" }
        this.walkTopDown().filter(isClassFile).sumOf { scanClassFile(it, this) }
    }

    private fun scanJarFile(file: File) = file.run {
        val isNotScanned: (File) -> Boolean = { !scannedJarFiles.contains(it.absolutePath) }
        val toStream: (File) -> InputStream = { it.inputStream().buffered(JAR_BUFFER_SIZE) }
        this.takeIf(isNotScanned)?.let(toStream)?.let { scanJarInputStream(it, this) } ?: 0
    }

    private fun scanJarInputStream(stream: InputStream, file: File?): Int = JarInputStream(stream).use {
        var scanned = 0
        var jarEntry = it.nextJarEntry
        while (jarEntry != null) {
            when (jarEntry.takeUnless(JarEntry::isDirectory)?.name?.substringAfterLast('.')) {
                "jar", "war", "rar" -> scanned += scanJarInputStream(ByteArrayInputStream(it.readBytes()), null)
                "class" -> scanned += scanClassEntry(jarEntry, it)
            }
            jarEntry = it.nextJarEntry
        }
        file?.run {
            val classpathToFile: (String) -> File? = { cp -> File(this.parent, cp).takeIf(File::exists) }
            val classpath = it.manifest?.mainAttributes?.getValue(Attributes.Name.CLASS_PATH)?.split(" ") ?: emptyList()
            scannedJarFiles.add(this.absolutePath)
            scanned += classpath.mapNotNull(classpathToFile).sumOf(::scanJarFile)
        }
        scanned
    }

    private val isPrefixMatches: (ClassSource) -> Boolean = { it.prefixMatches(packagePrefixes) }
    private val isClassAccepted: (ClassSource) -> Boolean = {
        !it.entityName().contains('$') &&
                !it.entityName().startsWith(PACKAGE_DRILL) &&
                !it.entityName().startsWith(PACKAGE_TTL) &&
                it.prefixMatches(packagePrefixes) &&
                !scannedClasses.contains(it.entityName())
    }

    private fun scanClassFile(file: File, directory: File) = file.run {
        val readClassSource: (ClassSource) -> ClassSource = {
            val bytes = this.readBytes()
            val superName = ClassReader(bytes).superName ?: ""
            it.copy(superName = superName, bytes = bytes)
        }
        this.toRelativeString(directory).replace(File.separatorChar, '/')
            .removePrefix(PREFIX_WEB_APP).removePrefix(PREFIX_SPRING_BOOT).removeSuffix(".class").let(::ClassSource)
            .takeIf(isClassAccepted)?.let(readClassSource)?.takeIf(isPrefixMatches)?.let(::addClassToScanned) ?: 0
    }

    private fun scanClassEntry(entry: JarEntry, stream: JarInputStream) = entry.name.run {
        val readClassSource: (ClassSource) -> ClassSource = {
            val bytes = stream.readBytes()
            val superName = ClassReader(bytes).superName ?: ""
            it.copy(superName = superName, bytes = bytes)
        }
        this.removePrefix(PREFIX_WEB_APP).removePrefix(PREFIX_SPRING_BOOT).removeSuffix(".class").let(::ClassSource)
            .takeIf(isClassAccepted)?.let(readClassSource)?.takeIf(isPrefixMatches)?.let(::addClassToScanned) ?: 0
    }

    private fun addClassToScanned(classSource: ClassSource): Int {
        scannedClasses.add(classSource.entityName())
        scannedBuffer.add(classSource)
        if (scannedBuffer.size >= classesBufferSize) {
            transfer(scannedBuffer)
            scannedBuffer.clear()
        }
        return 1
    }

}
