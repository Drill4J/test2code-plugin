package com.epam.drill.plugins.test2code

import com.epam.drill.plugins.test2code.api.*
import com.epam.drill.plugins.test2code.common.api.*
import com.epam.kodux.*
import kotlinx.atomicfu.*
import kotlinx.collections.immutable.*
import kotlinx.serialization.*

sealed class AgentData

object NoData : AgentData()

class DataBuilder : AgentData(), Iterable<AstEntity> {

    private val _data = atomic(persistentListOf<AstEntity>())

    operator fun plusAssign(parts: Iterable<AstEntity>) = _data.update { it + parts }

    override fun iterator() = _data.value.iterator()
}

@Serializable
data class ClassesData(
    @Id val buildVersion: String,
    val prevBuildVersion: String,
    val prevBuildCoverage: Double,
    val packageTree: PackageTree,
    val scopeCounter: Int = 1
) : AgentData()
