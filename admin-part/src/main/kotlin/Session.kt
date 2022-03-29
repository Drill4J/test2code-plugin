/**
 * Copyright 2020 - 2022 EPAM Systems
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
import com.epam.drill.plugins.test2code.util.*
import com.epam.dsm.util.*
import kotlinx.atomicfu.*
import kotlinx.collections.immutable.*
import kotlinx.serialization.*

private val logger = logger {}

@Serializable
sealed class Session : Sequence<ExecClassData> {
    abstract val id: String
    abstract val testType: String

    /**
     * All test received from autotest agent as [TestInfo] and for manual tests from [ExecClassData]
     */
    abstract val tests: Set<TestOverview>
}


private typealias ProbeKey = Pair<String, String>

class ActiveSession(
    override val id: String,
    override val testType: String,
    val isGlobal: Boolean = false,
    val isRealtime: Boolean = false,
) : Session() {

    override val tests: Set<TestOverview>
        get() = _probes.value.run {
            _testTestInfo.value.takeIf { it.any() }?.let { tests ->
                val autotests = tests.values.map {
                    TestOverview(
                        testId = it.id.weakIntern(),
                        duration = it.finishedAt - it.startedAt,
                        details = it.details,
                        result = it.result
                    )
                }
                val manualTests = keys.filter { (id, _) -> id !in tests }.map { (id, name) ->
                    TestOverview(testId = id, details = TestDetails(testName = name.urlDecode().weakIntern()))
                }
                autotests + manualTests
            } ?: keys.map { (id, name) ->
                TestOverview(testId = id, details = TestDetails(testName = name.urlDecode().weakIntern()))
            }
        }.toSet()

    private val _probes = atomic(
        persistentMapOf<ProbeKey, PersistentMap<Long, ExecClassData>>()
    )

    private val _testTestInfo = atomic<PersistentMap<String, TestInfo>>(persistentHashMapOf())

    fun addAll(dataPart: Collection<ExecClassData>) = dataPart.map { probe ->
        probe.id?.let { probe } ?: probe.copy(id = probe.id())
    }.forEach { probe ->
        if (true in probe.probes) {
            val test = probe.testId.weakIntern() to probe.testName.weakIntern()
            _probes.update { map ->
                (map[test] ?: persistentHashMapOf()).let { testData ->
                    val probeId = probe.id()
                    if (probeId in testData) {
                        testData.getValue(probeId).run {
                            val merged = probes.merge(probe.probes)
                            merged.takeIf { it != probes }?.let {
                                testData.put(probeId, copy(probes = merged))
                            }
                        }
                    } else testData.put(probeId, probe)
                }?.let { map.put(test, it) } ?: map
            }
        }
    }

    fun addTests(testRun: List<TestInfo>) {
        _testTestInfo.update { current ->
            current.putAll(testRun.associateBy { it.id })
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
            tests = tests,
            probes = values.flatMap { it.values },
        )
    }
}

@Serializable
data class FinishedSession(
    override val id: String,
    override val testType: String,
    override val tests: Set<TestOverview>,
    val probes: List<ExecClassData>,
) : Session() {
    override fun iterator(): Iterator<ExecClassData> = probes.iterator()

    override fun equals(other: Any?): Boolean = other is FinishedSession && id == other.id

    override fun hashCode(): Int = id.hashCode()
}

