package com.epam.drill.plugins.test2code.coverage

import com.epam.drill.plugins.test2code.api.*

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

sealed class NamedCounter {
    abstract val name: String
    abstract val count: Count
}

class BundleCounter(
    override val name: String,
    override val count: Count = zeroCount,
    val methodCount: Count = zeroCount,
    val packages: List<PackageCounter> = emptyList()
) : NamedCounter()

class PackageCounter(
    override val name: String,
    override val count: Count,
    val classCount: Count,
    val methodCount: Count,
    val classes: List<ClassCounter>
) : NamedCounter()

class ClassCounter(
    val path: String,
    override val name: String,
    override val count: Count,
    val methods: List<MethodCounter>
) : NamedCounter() {
    val fullName = if (path.any()) "$path/$name" else name
}

class MethodCounter(
    override val name: String,
    val desc: String,
    val decl: String,
    override val count: Count
) : NamedCounter() {
    val sign = "$name$desc"
}
