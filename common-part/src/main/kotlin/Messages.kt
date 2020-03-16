package com.epam.drill.plugins.test2code

import kotlinx.serialization.*

@Polymorphic
@Serializable
abstract class CoverMessage

@SerialName("INIT")
@Serializable
data class InitInfo(
    val classesCount: Int,
    val message: String
) : CoverMessage()

@SerialName("INITIALIZED")
@Serializable
data class Initialized(val msg: String = "") : CoverMessage()

@SerialName("SESSION_STARTED")
@Serializable
data class SessionStarted(val sessionId: String, val testType: String, val ts: Long) : CoverMessage()

@SerialName("SESSION_CANCELLED")
@Serializable
data class SessionCancelled(val sessionId: String, val ts: Long) : CoverMessage()

@SerialName("COVERAGE_DATA_PART")
@Serializable
data class CoverDataPart(val sessionId: String, val data: List<ExecClassData>) : CoverMessage()

@SerialName("SESSION_FINISHED")
@Serializable
data class SessionFinished(val sessionId: String, val ts: Long) : CoverMessage()
