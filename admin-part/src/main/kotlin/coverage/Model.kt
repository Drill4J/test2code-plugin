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
import kotlinx.serialization.*

internal data class DiffMethods(
    val new: List<Method> = emptyList(),
    val modified: List<Method> = emptyList(),
    val deleted: List<Method> = emptyList(),
    val unaffected: List<Method> = emptyList(),
    val deletedWithCoverage: Map<Method, Count> = emptyMap(),
) {
    override fun equals(other: Any?): Boolean = super.equals(other)

    override fun hashCode(): Int = super.hashCode()
}

data class TestDurations(
    val all: Long = 0L,
    val byType: Map<String, Long> = emptyMap(),
)

internal data class CoverContext(
    val agentType: String,
    val packageTree: PackageTree,
    val methods: List<Method>,
    val methodChanges: DiffMethods = DiffMethods(),
    val probeIds: Map<String, Long> = emptyMap(),
    val build: CachedBuild,
    val parentBuild: CachedBuild? = null,
    val testsToRun: GroupedTests = emptyMap(),
    val testsToRunParentDurations: TestDurations = TestDurations(),
) {
    override fun equals(other: Any?): Boolean = super.equals(other)

    override fun hashCode(): Int = super.hashCode()
}

data class CoverageKey(
    val id: String,
    val packageName: String = "",
    val className: String = "",
    val methodName: String = "",
    val methodDesc: String = "",
) {
    override fun equals(other: Any?) = other is CoverageKey && id == other.id

    override fun hashCode() = id.hashCode()
}

/**
 * BundleCounters показывает ковередж с разных разрезов.
 * for example, all - shows all coverage of a build
 */
@Serializable
class BundleCounters(
    val all: BundleCounter,
    val testTypeOverlap: BundleCounter,
    val overlap: BundleCounter,
    val byTestType: Map<String, BundleCounter> = emptyMap(),
    val byTest: Map<TypedTest, BundleCounter> = emptyMap(),
    val detailsByTest: Map<TypedTest, TestDetails> = emptyMap(),
) : JvmSerializable {
    companion object {
        val empty = BundleCounter("").let {
            BundleCounters(all = it, testTypeOverlap = it, overlap = it)
        }
    }
}

sealed class NamedCounter : JvmSerializable {
    abstract val name: String
    abstract val count: Count
}

/**
 * BundleCounter - it is for mapping coverage by smth
 * показывает ковередж относительно от общего кол-ва.
 * Structure: overview vision -> packages -> classes -> methods.
 */
@Serializable
data class BundleCounter(
    override val name: String,
    override val count: Count = zeroCount,
    val methodCount: Count = zeroCount,
    val classCount: Count = zeroCount,
    val packageCount: Count = zeroCount,
    val packages: List<PackageCounter> = emptyList(),
) : NamedCounter(), JvmSerializable {
    companion object {
        val empty = BundleCounter("")
    }
}

@Serializable
data class PackageCounter(
    override val name: String,
    override val count: Count,
    val classCount: Count,
    val methodCount: Count,
    val classes: List<ClassCounter>,
) : NamedCounter(), JvmSerializable

@Serializable
data class ClassCounter(
    val path: String,
    override val name: String,
    override val count: Count,
    val methods: List<MethodCounter>,
    val fullName: String,
    val probes: List<Boolean> = emptyList(),
) : NamedCounter(), JvmSerializable

@Serializable
data class MethodCounter(
    override val name: String,
    val desc: String,
    val decl: String,
    val sign: String,
    val fullName: String,
    override val count: Count,
) : NamedCounter(), JvmSerializable

@Serializable
class BundleCountersDto(
    val all: BundleCounterDto,
    val testTypeOverlap: BundleCounterDto,
    val overlap: BundleCounterDto,
    val byTestType: Map<String, BundleCounterDto> = emptyMap(),
    val byTest: Map<String, BundleCounterDto> = emptyMap(),
    val detailsByTest: Map<String, TestDetails> = emptyMap(),
) : JvmSerializable {
    companion object {
        val empty = BundleCounterDto("").let {
            BundleCountersDto(all = it, testTypeOverlap = it, overlap = it)
        }
    }
}

sealed class NamedCounterDto : JvmSerializable {
    abstract val name: String
    abstract val count: CountDto
}

@Serializable
data class BundleCounterDto(
    override val name: String,
    override val count: CountDto = zeroCountDto,
    val methodCount: CountDto = zeroCountDto,
    val classCount: CountDto = zeroCountDto,
    val packageCount: CountDto = zeroCountDto,
    val packages: List<PackageCounterDto> = emptyList(),
) : NamedCounterDto(), JvmSerializable {
    companion object {
        val empty = BundleCounterDto("")
    }
}

@Serializable
data class PackageCounterDto(
    override val name: String,
    override val count: CountDto,
    val classCount: CountDto,
    val methodCount: CountDto,
    val classes: List<ClassCounterDto>,
) : NamedCounterDto(), JvmSerializable

@Serializable
data class ClassCounterDto(
    val path: String,
    override val name: String,
    override val count: CountDto,
    val methods: List<MethodCounterDto>,
    val fullName: String,
    val probes: List<Boolean> = emptyList(),
) : NamedCounterDto(), JvmSerializable

@Serializable
data class MethodCounterDto(
    override val name: String,
    val desc: String,
    val decl: String,
    val sign: String,
    val fullName: String,
    override val count: CountDto,
) : NamedCounterDto(), JvmSerializable
