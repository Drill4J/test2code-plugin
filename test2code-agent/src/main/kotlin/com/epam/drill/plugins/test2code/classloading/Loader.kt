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

fun scanClasses(
    scanPackages: Iterable<String>,
    classesBufferSize: Int = 50,
    consumer: (Set<EntitySource>) -> Unit
): Int {
    val threadClassLoaders = Thread.getAllStackTraces().keys.mapNotNull(Thread::getContextClassLoader)
    val leafClassLoaders = threadClassLoaders
        .leaves(ClassLoader::getParent)
        .toListWith(ClassLoader.getSystemClassLoader())
    return ClassHandler(scanPackages, classesBufferSize, consumer).scan(leafClassLoaders)
}
