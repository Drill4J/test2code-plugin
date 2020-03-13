package com.epam.drill.plugins.test2code

import com.epam.kodux.*
import kotlinx.serialization.*

sealed class AgentData

object NoData : AgentData()

object ClassDataBuilder : AgentData()

@Serializable
class ClassesData(
    @Id val buildVersion: String,
    val totalInstructions: Int,
    val prevBuildVersion: String,
    val prevBuildCoverage: Double,
    val packageTree: List<JavaPackageCoverage>
) : AgentData()
