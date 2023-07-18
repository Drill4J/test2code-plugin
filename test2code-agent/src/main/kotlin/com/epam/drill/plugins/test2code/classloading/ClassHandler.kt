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

import com.epam.drill.plugin.api.processing.EntitySource
import java.io.*
import java.net.*
import java.util.jar.*

/**
 * Buffering and handling scanned classes
 * @param includedPaths package prefixes for scanning
 * @param bufferSize the size of classes buffer before handling
 * @param consumer handler of scanned classes
 */
class ClassHandler(
    private val includedPaths: Iterable<String>,
    private val bufferSize: Int = 50,
    private val consumer: (Set<EntitySource>) -> Unit
) {
    private val scannedUrls = mutableSetOf<URL>()

    private val scannedNames = mutableSetOf<String>()

    private val scannedClasses: MutableSet<EntitySource> = mutableSetOf()

    private var scannedClassesCount = 0

    /**
     * Scan classes using class loaders
     * @param classLoaders the list of class loaders
     * @return the total number of scanned classes
     */
    fun scan(classLoaders: Iterable<ClassLoader>): Int {
        val allClassLoaders = classLoaders.flatMapTo(mutRefSet()) {
            it.parents(ClassLoader::getParent) + it
        }
        allClassLoaders.forEach { classLoader ->
            classLoader.urls().forEach { url ->
                url.scan(classLoader)
            }
        }
        if (scannedClasses.isNotEmpty())
            consumer(scannedClasses)
        return scannedClassesCount
    }

    private fun URL.scan(classloader: ClassLoader): Unit = takeIf { scannedUrls.add(it) }?.toFile()?.let { file ->
        if (file.isDirectory) {
            file.scan(::predicate, ::handler)
        } else file.useJarInputStream {
            it.scan(::predicate, ::handler)
            it.manifest?.classPath(this)?.forEach { url ->
                url.scan(classloader)
            }
        }
    } ?: Unit

    private fun Manifest.classPath(jarUrl: URL): Set<URL>? = mainAttributes.run {
        getValue(Attributes.Name.CLASS_PATH.toString())?.split(" ")
    }?.mapNotNullTo(mutableSetOf()) { path ->
        jarUrl.resolve(path)?.takeIf { it.protocol == "file" }
    }

    private fun predicate(
        source: ClassSource
    ): Boolean = source.matches(includedPaths) && source.className !in scannedNames

    private fun handler(source: ClassSource) {
        scannedNames.add(source.className)
        scannedClasses.add(source)
        scannedClassesCount++
        if (scannedClasses.size >= bufferSize) {
            consumer(scannedClasses)
            scannedClasses.clear()
        }
    }
}

private fun ClassLoader.urls(): List<URL> = when (this) {
    ClassLoader.getSystemClassLoader() -> parseJavaClassPath()
    is URLClassLoader -> urLs.toList()
    else -> emptyList()
}.filter { it.protocol == "file" }

private fun parseJavaClassPath(): List<URL> = System.getProperty("java.class.path")
    .split(File.pathSeparator)
    .mapNotNull(String::toUrl)


private fun String.toUrl(): URL? = try {
    File(this).toURI().toURL()
} catch (e: Exception) {
    when (e) {
        is SecurityException, is MalformedURLException -> null
        else -> throw e
    }
}

private fun URL.resolve(path: String): URL? = try {
    URL(this, path)
} catch (e: MalformedURLException) {
    null
}

private fun URL.toFile(): File? = try {
    File(toURI()).takeIf { it.exists() }  // Accepts escaped characters like %20.
} catch (e: Exception) { // URL.toURI() doesn't escape chars.
    when (e) {
        is URISyntaxException -> File(path) // Accepts non-escaped chars like space.
        else -> null
    }
}
