package com.epam.drill.plugins.test2code

import com.epam.drill.plugins.test2code.api.*
import com.epam.drill.plugins.test2code.common.api.*
import com.epam.kodux.*
import kotlinx.atomicfu.*
import kotlinx.collections.immutable.*
import kotlinx.serialization.*

internal sealed class AgentData

internal object NoData : AgentData()

internal class DataBuilder : AgentData(), Iterable<AstEntity> {

    private val _data = atomic(persistentListOf<AstEntity>())

    operator fun plusAssign(parts: Iterable<AstEntity>) = _data.update { it + parts }

    override fun iterator() = _data.value.iterator()
}

@Serializable
internal data class ClassData(
    @Id val buildVersion: String,
    val packageTree: PackageTree,
    val methods: List<Method>,
    val methodChanges: DiffMethods,
    val probeIds: Map<String, Long> = emptyMap()
) : AgentData() {
    override fun equals(other: Any?) = other is ClassData && buildVersion == other.buildVersion

    override fun hashCode() = buildVersion.hashCode()
}

@Serializable
internal class DiffMethods(
    val new: List<Method> = emptyList(),
    val modified: List<Method> = emptyList(),
    val deleted: List<Method> = emptyList(),
    val unaffected: List<Method> = emptyList()
)
