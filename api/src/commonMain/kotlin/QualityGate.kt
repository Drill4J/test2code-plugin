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
    FAILED,
}

@Serializable
data class QualityGate(
    val status: GateStatus,
    val results: Map<String, Boolean> = emptyMap()
)

enum class ConditionOp { LT, LTE, GT, GTE }

@Serializable
data class QualityGateCondition(
    val measure: String,
    val operator: ConditionOp,
    val value: Double
)
