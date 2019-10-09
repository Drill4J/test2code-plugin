package com.epam.drill.plugins.coverage

import com.epam.drill.common.*
import com.epam.kodux.*
import kotlinx.serialization.*

sealed class AgentData

object NoData : AgentData()

object ClassDataBuilder : AgentData()

@Serializable
class ClassesData(
    @Id
    val buildVersion: String,
    val totalInstructions: Int,
    val prevAgentInfo: AgentInfo,
    val prevBuildCoverage: Double
) : AgentData()
