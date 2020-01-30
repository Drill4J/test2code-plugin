package com.epam.drill.plugins.test2code

import kotlinx.atomicfu.*
import kotlinx.collections.immutable.*
import kotlinx.serialization.*

@Serializable
sealed class Session(
    val id: String,
    val testType: String
)

class ActiveSession(
    id: String,
    testType: String
) : Session(id, testType) {

    private val _probes = atomic(persistentListOf<ExecClassData>())

    fun append(probe: ExecClassData) {
        _probes.update { it.add(probe) }
    }

    fun finish() = FinishedSession(
        sessionId = id,
        testTypeName = testType,
        probes = _probes.value.asSequence().groupBy { TypedTest(it.testName, testType) }
    )
}

@Serializable
class FinishedSession(
    val sessionId: String,
    val testTypeName: String,
    val probes: Map<TypedTest, List<ExecClassData>>
) : Session(sessionId, testTypeName), Sequence<ExecClassData> {

    val testNames = probes.keys

    override fun iterator() = probes.asSequence()
        .flatMap { it.value.asSequence() }
        .iterator()
}

@Serializable
data class TypedTest(
    val name: String,
    val type: String
) {
    val id get() = "$name:$type"
}
