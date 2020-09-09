package com.epam.drill.plugins.test2code.common.api

import kotlinx.serialization.*

@Serializable
data class CoverConfig(
    val message: String = ""
)

@Serializable
data class AstEntity(
    val path: String,
    val name: String,
    val methods: List<AstMethod>
)

@Serializable
data class AstMethod(
    val name: String,
    val params: List<String>,
    val returnType: String,
    val count: Int = 0,
    val probes: List<Int> = emptyList(),
    val checksum: String = ""
)

@Serializable
data class InitScopePayload(
    val id: String,
    val name: String,
    val prevId: String
)

@Serializable
data class StartSessionPayload(
    val sessionId: String,
    val testType: String,
    val isRealtime: Boolean
)

@Serializable
data class AgentSessionPayload(
    val sessionId: String
)

@Serializable
data class ExecClassData(
    val id: Long = 0L,
    val className: String,
    val probes: List<Boolean>,
    val testName: String = ""
)
