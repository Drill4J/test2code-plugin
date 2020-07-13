package com.epam.drill.plugins.test2code.api

import kotlinx.serialization.*

@Serializable
sealed class Setting

@Serializable
@SerialName("condition")
data class ConditionSetting(
    val enabled: Boolean,
    val condition: QualityGateCondition
) : Setting()
