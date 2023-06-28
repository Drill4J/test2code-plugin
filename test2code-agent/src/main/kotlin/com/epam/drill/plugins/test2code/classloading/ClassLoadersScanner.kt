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

import java.io.File
import java.net.URI
import java.net.URL
import java.net.URLClassLoader
import java.net.MalformedURLException

class ClassLoadersScanner(
    packagePrefixes: List<String>,
    classesBufferSize: Int = 50,
    transfer: (Set<ClassSource>) -> Unit
) {

    private val classPathScanner = ClassPathScanner(packagePrefixes, classesBufferSize, transfer)

    fun scanClassLoaders() = Thread.getAllStackTraces().keys.mapNotNull(Thread::getContextClassLoader)
        .fold(mutableSetOf(ClassLoader.getSystemClassLoader()), ::addClassLoaderWithParents)

    fun scanClassLoadersURIs(classloaders: Set<ClassLoader>) = classloaders
        .fold(getSystemClassPath().toMutableSet(), ::addClassLoaderURIs).let(::normalizeURIs)

    fun scanClassLoadersURIs() = scanClassLoadersURIs(scanClassLoaders())

    fun scanClasses(uris: Set<URI>) = uris.fold(0, ::scanClassLoaderURI).also { classPathScanner.transferBuffer() }

    fun scanClasses() = scanClasses(scanClassLoadersURIs())

    private fun addClassLoaderWithParents(loaders: MutableSet<ClassLoader>, classloader: ClassLoader) = loaders.apply {
        var current: ClassLoader? = classloader
        while (current != null) {
            this.add(current)
            current = current.parent
        }
    }

    private fun addClassLoaderURIs(uris: MutableSet<URI>, classloader: ClassLoader) = uris.apply {
        if (classloader is URLClassLoader) this.addAll(classloader.urLs.map(URL::toURI))
        this.addAll(classloader.getResources("/").asSequence().map(URL::toURI))
    }

    private fun scanClassLoaderURI(count: Int, uri: URI) = count + classPathScanner.scanURI(uri)

    private fun getSystemClassPath() = System.getProperty("java.class.path").split(File.pathSeparator).mapNotNull {
        try {
            File(it).takeIf(File::exists)?.toURI()
        } catch (e: SecurityException) {
            null
        } catch (e: MalformedURLException) {
            null
        }
    }

    private fun normalizeURIs(uris: Set<URI>) = mutableSetOf<URI>().apply {
        val isFileExists: (URI) -> Boolean = {  File(it).exists() }
        val isNormalized: (URI) -> Boolean = { uri -> this.any { uri.path.startsWith(it.path) } }
        val normalizePath: (URI) -> URI = {
            val path = it.takeUnless(URI::isOpaque)?.let(URI::getPath) ?: it.schemeSpecificPart.removePrefix("file:")
            URI("file", null, path.removeSuffix("!/"), null)
        }
        uris.map(normalizePath).forEach {
            it.takeUnless(isNormalized)?.let { uri ->
                uri.takeIf(isFileExists)?.let(this::add) ?: retrieveFileURI(uri)?.let(this::add)
            }
        }
    }

    private fun retrieveFileURI(uri: URI) = uri.run {
        val isArchiveContains: (String) -> Boolean = { it.contains(Regex("\\.jar/|\\.war/|\\.rar/|\\.ear/")) }
        val isArchiveEnds: (String) -> Boolean = { it.contains(Regex("\\.jar$|\\.war$|\\.rar$|\\.ear$")) }
        var path = File(this).invariantSeparatorsPath
        while (!File(path).exists() && isArchiveContains(path)) {
            path = path.substringBeforeLast("/")
        }
        path.takeIf(isArchiveEnds)?.let(::File)?.takeIf(File::exists)?.toURI()
    }

}
