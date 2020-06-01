package com.epam.drill.plugins.test2code

import com.epam.drill.plugins.test2code.api.*
import com.epam.drill.plugins.test2code.common.api.*
import kotlinx.atomicfu.*
import kotlinx.collections.immutable.*
import kotlinx.serialization.*

@Serializable
sealed class Session : Sequence<ExecClassData> {
    abstract val id: String
    abstract val testType: String
    abstract val tests: Set<TypedTest>
}

class ActiveSession(
    override val id: String,
    override val testType: String
) : Session() {

    private val _probes = atomic(
        persistentMapOf<TypedTest, PersistentMap<Long, ExecClassData>>()
    )

    override val tests: Set<TypedTest>
        get() = _probes.value.keys

    fun addAll(dataPart: Collection<ExecClassData>) = _probes.update {
        it.mutate { map ->
            for (probe in dataPart) {
                val key = TypedTest(probe.testName, testType)
                val value = (map[key] ?: persistentHashMapOf()).let { testData ->
                    val probeId = probe.id()
                    val value = testData[probeId]?.merge(probe) ?: probe
                    testData.put(probeId, value)
                }
                map[key] = value
            }
        }
    }

    override fun iterator(): Iterator<ExecClassData> = Sequence {
        _probes.value.values.asSequence().flatMap { it.values.asSequence() }.iterator()
    }.iterator()

    fun finish() = _probes.value.run {
        FinishedSession(
            id = id,
            testType = testType,
            tests = keys,
            probes = values.flatMap { it.values }
        )
    }
}

@Serializable
data class FinishedSession(
    override val id: String,
    override val testType: String,
    override val tests: Set<TypedTest>,
    val probes: List<ExecClassData>
) : Session() {
    override fun iterator(): Iterator<ExecClassData> = probes.iterator()

    override fun equals(other: Any?): Boolean = other is FinishedSession && id == other.id

    override fun hashCode(): Int = id.hashCode()
}

internal fun TypedTest.id() = "$name:$type"

private fun ExecClassData.id(): Long = id.takeIf { it != 0L } ?: className.crc64()

internal fun ExecClassData.merge(other: ExecClassData): ExecClassData = copy(
    probes = probes.merge(other.probes)
)

internal fun List<Boolean>.merge(other: List<Boolean>): List<Boolean> = mapIndexed { i, b ->
    if (i < other.size) {
        b || other[i]
    } else b
}
