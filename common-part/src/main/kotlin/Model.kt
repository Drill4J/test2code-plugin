package com.epam.drill.plugins.test2code

import kotlinx.serialization.*

@Serializable
data class CoverConfig(
    val message: String = ""
)

@Serializable
data class StartPayload(val testType: String = "MANUAL", val sessionId: String = "")

@Serializable
data class StartSessionPayload(val sessionId: String, val startPayload: StartPayload)

@Serializable
data class SessionPayload(val sessionId: String)

@Serializable
data class ExecClassData(
    val id: Long,
    val className: String,
    val probes: List<Boolean>,
    val testName: String = ""
)
