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
package com.epam.drill.plugins.test2code.coverage

import com.epam.drill.plugins.test2code.*
import com.epam.drill.plugins.test2code.api.*
import com.epam.drill.plugins.test2code.util.*
import kotlinx.serialization.*

internal data class DiffMethods(
    val new: List<Method> = emptyList(),
    val modified: List<Method> = emptyList(),
    val deleted: List<Method> = emptyList(),
    val unaffected: List<Method> = emptyList(),
    val deletedWithCoverage: Map<Method, Count> = emptyMap()
) {
    override fun equals(other: Any?): Boolean = super.equals(other)

    override fun hashCode(): Int = super.hashCode()
}

data class TestDurations(
    val all: Long = 0L,
    val byType: Map<String, Long> = emptyMap()
)

internal data class CoverContext(
    val agentType: String,
    val packageTree: PackageTree,
    val methods: List<Method>,
    val methodChanges: DiffMethods = DiffMethods(),
    val probeIds: Map<String, Long> = emptyMap(),
    val classBytes: Map<String, ByteArray> = emptyMap(),
    val build: CachedBuild,
    val parentBuild: CachedBuild? = null,
    val testsToRun: GroupedTests = emptyMap(),
    val testsToRunParentDurations : TestDurations = TestDurations()
) {
    override fun equals(other: Any?): Boolean = super.equals(other)

    override fun hashCode(): Int = super.hashCode()
}

data class CoverageKey(
    val id: String,
    val packageName: String = "",
    val className: String = "",
    val methodName: String = "",
    val methodDesc: String = ""
) {
    override fun equals(other: Any?) = other is CoverageKey && id == other.id

    override fun hashCode() = id.hashCode()
}

@Serializable
class BundleCounters(
    val all: BundleCounter,
    val testTypeOverlap: BundleCounter,
    val overlap: BundleCounter,
    val byTestType: Map<String, BundleCounter> = emptyMap(),
    val byTest: Map<TypedTest, BundleCounter> = emptyMap(),
    val statsByTest: Map<TypedTest, TestStats> = emptyMap()
) {
    companion object {
        val empty = BundleCounter("").let {
            BundleCounters(all = it, testTypeOverlap = it, overlap = it)
        }
    }
}

sealed class NamedCounter {
    abstract val name: String
    abstract val count: Count
}

@Serializable
data class BundleCounter(
    @StringIntern
    override val name: String,
    override val count: Count = zeroCount,
    val methodCount: Count = zeroCount,
    val classCount: Count = zeroCount,
    val packageCount: Count = zeroCount,
    val packages: List<PackageCounter> = emptyList()
) : NamedCounter()

@Serializable
data class PackageCounter(
    @StringIntern
    override val name: String,
    override val count: Count,
    val classCount: Count,
    val methodCount: Count,
    val classes: List<ClassCounter>
) : NamedCounter()

@Serializable
data class ClassCounter(
    @StringIntern
    val path: String,
    @StringIntern
    override val name: String,
    override val count: Count,
    val methods: List<MethodCounter>
) : NamedCounter() {
    val fullName = if (path.any()) "$path/$name" else name
}

@Serializable
data class MethodCounter(
    @StringIntern
    override val name: String,
    @StringIntern
    val desc: String,
    @StringIntern
    val decl: String,
    override val count: Count
) : NamedCounter() {
    val sign = "$name$desc"
}
