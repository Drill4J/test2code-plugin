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
package com.epam.drill.plugins.test2code

import com.epam.drill.plugins.test2code.api.*
import com.epam.drill.plugins.test2code.common.api.*
import com.epam.drill.plugins.test2code.coverage.*
import kotlinx.atomicfu.*
import kotlinx.collections.immutable.*
import kotlinx.serialization.*

private val logger = logger {}

@Serializable
sealed class Session : Sequence<ExecClassData> {
    abstract val id: String
    abstract val testType: String
    abstract val tests: Set<TypedTest>
    abstract val testOverview: Map<TypedTest, TestOverview>
}

class ActiveSession(
    override val id: String,
    override val testType: String, // TODO Replace on list of labels
    val isGlobal: Boolean = false,
    val isRealtime: Boolean = false,
) : Session() {

    override val tests: Set<TypedTest>
        get() = _probes.value.keys

    override val testOverview: Map<TypedTest, TestOverview>
        get() = _testTestInfo.value.associate {
            TypedTest(type = testType, id = it.id) to TestOverview(
                duration = it.finishedAt - it.startedAt,
                details = it.details,
                result = it.result
            )
        }

    private val _probes = atomic(
        persistentMapOf<TypedTest, PersistentMap<Long, ExecClassData>>()
    )

    private val _testTestInfo = atomic<List<TestInfo>>(emptyList())

    fun addAll(dataPart: Collection<ExecClassData>) = dataPart.map { probe ->
        probe.id?.let { probe } ?: probe.copy(id = probe.id())
    }.forEach { probe ->
        if (true in probe.probes) {
            val typedTest = probe.testId.typedTest(testType)
            _probes.update { map ->
                (map[typedTest] ?: persistentHashMapOf()).let { testData ->
                    val probeId = probe.id()
                    if (probeId in testData) {
                        testData.getValue(probeId).run {
                            val merged = probes.merge(probe.probes)
                            merged.takeIf { it != probes }?.let {
                                testData.put(probeId, copy(probes = merged))
                            }
                        }
                    } else testData.put(probeId, probe.copy(testId = typedTest.id))
                }?.let { map.put(typedTest, it) } ?: map
            }
        }
    }

    fun addTests(testRun: List<TestInfo>) {
        _testTestInfo.update { current ->
            current + testRun
        }
    }

    override fun iterator(): Iterator<ExecClassData> = Sequence {
        _probes.value.values.asSequence().flatMap { it.values.asSequence() }.iterator()
    }.iterator()

    fun finish() = _probes.value.run {
        logger.debug { "ActiveSession finish with size = ${_probes.value.size} " }
        FinishedSession(
            id = id,
            testType = testType,
            //TODO remove HasSet
            tests = HashSet(_testTestInfo.value.takeIf { it.any() }?.let { tests ->
                keys + tests.map { it.id.typedTest(testType) }
            } ?: keys),
            testOverview = _testTestInfo.value.associate {
                TypedTest(type = testType, id = it.id) to TestOverview(
                    duration = it.finishedAt - it.startedAt,
                    result = it.result,
                    details = it.details
                )
            },
            probes = values.flatMap { it.values },
        )
    }
}

@Serializable
data class FinishedSession(
    override val id: String,
    override val testType: String,
    override val tests: Set<TypedTest>,
    override val testOverview: Map<TypedTest, TestOverview> = emptyMap(),
    val probes: List<ExecClassData>,
) : Session() {
    override fun iterator(): Iterator<ExecClassData> = probes.iterator()

    override fun equals(other: Any?): Boolean = other is FinishedSession && id == other.id

    override fun hashCode(): Int = id.hashCode()
}

