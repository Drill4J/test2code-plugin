package com.epam.drill.plugins.test2code

import com.epam.drill.plugins.test2code.api.*
import com.epam.drill.plugins.test2code.common.api.*
import kotlinx.atomicfu.*
import kotlinx.collections.immutable.*
import kotlinx.serialization.*

@Serializable
sealed class Session(
    val id: String,
    val testType: String
) {
    abstract val probes: Map<TypedTest, Map<Long, ExecClassData>>
}

class ActiveSession(
    id: String,
    testType: String
) : Session(id, testType) {

    override val probes get() = _probes.value

    private val _probes = atomic(
        persistentMapOf<TypedTest, PersistentMap<Long, ExecClassData>>()
    )

    fun addAll(dataPart: Collection<ExecClassData>) = _probes.update {
        it.mutate { map ->
            for (probe in dataPart) {
                val key = TypedTest(probe.testName, testType)
                val value = (map[key] ?: emptyTestData).let { testData ->
                    val value = testData[probe.id]?.merge(probe) ?: probe
                    testData.put(probe.id, value)
                }
                map[key] = value
            }
        }
    }

    fun finish() = FinishedSession(
        sessionId = id,
        testTypeName = testType,
        probes = probes.toMap()
    )

    companion object {
        private val emptyTestData = persistentHashMapOf<Long, ExecClassData>()
    }
}

@Serializable
class FinishedSession(
    val sessionId: String,
    val testTypeName: String,
    override val probes: Map<TypedTest, Map<Long, ExecClassData>>
) : Session(sessionId, testTypeName)

internal fun TypedTest.id() = "$name:$type"


private fun ExecClassData.merge(other: ExecClassData): ExecClassData = copy(
    probes = probes.merge(other.probes)
)

private fun List<Boolean>.merge(other: List<Boolean>): List<Boolean> = mapIndexed { i, b ->
    if (i < other.size) {
        b || other[i]
    } else b
}
