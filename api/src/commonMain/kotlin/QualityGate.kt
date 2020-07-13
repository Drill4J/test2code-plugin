package com.epam.drill.plugins.test2code.api

import kotlinx.serialization.*

@Serializable
data class StatsDto(
    val coverage: Double,
    val risks: Int,
    val tests: Int
)

enum class GateStatus {
    PASSED,
    WARNING,
    FAILED,
}

@Serializable
data class QualityGate(
    val status: GateStatus,
    val results: Map<String, Boolean>
)

enum class ConditionOp { LT, GT }

@Serializable
data class QualityGateCondition(
    val measure: String,
    val operator: ConditionOp,
    val value: Double
)