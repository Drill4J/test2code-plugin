package com.epam.drill.plugins.test2code.coverage

import com.epam.drill.common.*
import com.epam.drill.plugins.test2code.*
import com.epam.drill.plugins.test2code.api.*
import kotlinx.serialization.Serializable

internal class CoverContext(
    val agentType: AgentType,
    val packageTree: PackageTree,
    val methods: List<Method>,
    val methodChanges: DiffMethods,
    val probeIds: Map<String, Long> = emptyMap(),
    val classBytes: Map<String, ByteArray> = emptyMap(),
    val build: CachedBuild? = null
)

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
    val byTestType: Map<String, BundleCounter> = emptyMap(),
    val byTest: Map<TypedTest, BundleCounter> = emptyMap()
) {
    companion object {
        val empty = BundleCounters(all = BundleCounter(""))
    }
}

sealed class NamedCounter {
    abstract val name: String
    abstract val count: Count
}

@Serializable
class BundleCounter(
    override val name: String,
    override val count: Count = zeroCount,
    val methodCount: Count = zeroCount,
    val packages: List<PackageCounter> = emptyList()
) : NamedCounter()

@Serializable
class PackageCounter(
    override val name: String,
    override val count: Count,
    val classCount: Count,
    val methodCount: Count,
    val classes: List<ClassCounter>
) : NamedCounter()

@Serializable
class ClassCounter(
    val path: String,
    override val name: String,
    override val count: Count,
    val methods: List<MethodCounter>
) : NamedCounter() {
    val fullName = if (path.any()) "$path/$name" else name
}

@Serializable
class MethodCounter(
    override val name: String,
    val desc: String,
    val decl: String,
    override val count: Count
) : NamedCounter() {
    val sign = "$name$desc"
}

@Serializable
data class CoverageByTests(
    val all: TestSummary,
    val byType: List<TestTypeSummary>
)
