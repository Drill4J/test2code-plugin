package com.epam.drill.plugins.test2code

import com.epam.drill.plugins.test2code.api.*
import com.epam.drill.plugins.test2code.common.api.*
import com.epam.drill.plugins.test2code.coverage.*
import kotlinx.atomicfu.*
import kotlinx.collections.immutable.*
import kotlinx.serialization.*

@Serializable
sealed class Session : Sequence<ExecClassData> {
    abstract val id: String
    abstract val testType: String
    abstract val tests: Set<TypedTest>
    abstract val testStats: Map<TypedTest, TestStats>
}

class ActiveSession(
    override val id: String,
    override val testType: String
) : Session() {

    override val tests: Set<TypedTest>
        get() = _probes.value.keys

    override val testStats: Map<TypedTest, TestStats> = emptyMap()

    private val _probes = atomic(
        persistentMapOf<TypedTest, PersistentMap<Long, ExecClassData>>()
    )

    private val _testRun = atomic<TestRun?>(null)

    fun addAll(dataPart: Collection<ExecClassData>) = dataPart.forEach { probe ->
        if (true in probe.probes) {
            val typedTest = TypedTest(probe.testName, testType)
            _probes.update { map ->
                val testData = map[typedTest] ?: persistentHashMapOf()
                val probeId = probe.id()
                val merged = testData[probeId]?.merge(probe) ?: probe
                map.put(typedTest, testData.put(probeId, merged))
            }
        }
    }

    fun setTestRun(testRun: TestRun) {
        _testRun.value = testRun
    }

    override fun iterator(): Iterator<ExecClassData> = Sequence {
        _probes.value.values.asSequence().flatMap { it.values.asSequence() }.iterator()
    }.iterator()

    fun finish() = _probes.value.run {
        FinishedSession(
            id = id,
            testType = testType,
            tests = _testRun.value?.tests?.takeIf { it.any() }?.let { tests ->
                keys + tests.map { TypedTest(type = testType, name = it.name) }
            } ?: keys,
            testStats = _testRun.value?.tests?.associate {
                TypedTest(type = testType, name = it.name) to TestStats(
                    duration = it.finishedAt - it.startedAt,
                    result = it.result
                )
            } ?: emptyMap(),
            probes = values.flatMap { it.values }
        )
    }
}

@Serializable
data class FinishedSession(
    override val id: String,
    override val testType: String,
    override val tests: Set<TypedTest>,
    override val testStats: Map<TypedTest, TestStats> = emptyMap(),
    val probes: List<ExecClassData>
) : Session() {
    override fun iterator(): Iterator<ExecClassData> = probes.iterator()

    override fun equals(other: Any?): Boolean = other is FinishedSession && id == other.id

    override fun hashCode(): Int = id.hashCode()
}
