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

private const val SUBCLASS_OF = "!subclassOf:"

data class ClassSource(
    private val entityName: String,
    private val superName: String = "",
    private val bytes: ByteArray = byteArrayOf(),
) : EntitySource {

    override fun entityName() = entityName

    override fun bytes() = bytes

    override fun toString() = "$entityName: ${this::class.simpleName}"

    override fun equals(other: Any?) = other is ClassSource && entityName == other.entityName

    override fun hashCode() = entityName.hashCode()

    fun prefixMatches(prefixes: Iterable<String>): Boolean {
        val matchPrefix: () -> Boolean = {
            prefixes.any { entityName.regionMatches(0, it, 0, it.length) }
        }
        val notExcluded: () -> Boolean = {
            prefixes.none { it.startsWith('!') && entityName.regionMatches(0, it, 1, it.length - 1) }
        }
        val notSubclass: () -> Boolean = {
            prefixes.none {
                it.startsWith(SUBCLASS_OF) &&
                        superName.isNotBlank() &&
                        superName.regionMatches(0, it, SUBCLASS_OF.length, it.length - SUBCLASS_OF.length)
            }
        }
        return matchPrefix() && notExcluded() && notSubclass()
    }

}
