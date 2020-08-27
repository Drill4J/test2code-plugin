package com.epam.drill.plugins.test2code

import com.epam.drill.plugin.api.processing.*
import com.epam.drill.plugins.test2code.common.api.*
import kotlinx.atomicfu.*
import kotlinx.collections.immutable.*

/**
 * Provides boolean array for the probe.
 * Implementations must be kotlin singleton objects.
 */
typealias ProbeArrayProvider = (Long, String, Int) -> BooleanArray

typealias RealtimeHandler = (Sequence<ExecDatum>) -> Unit

interface SessionProbeArrayProvider : ProbeArrayProvider {
    fun start(sessionId: String, realtimeHandler: RealtimeHandler? = null)
    fun stop(sessionId: String): Sequence<ExecDatum>?
    fun cancel(sessionId: String)
    fun cancelAll(): List<String>
}

const val DRIlL_TEST_NAME = "drill-test-name"

class ExecDatum(
    val id: Long,
    val name: String,
    val probes: BooleanArray,
    val testName: String = ""
)

fun ExecDatum.toExecClassData() = ExecClassData(
    id = id,
    className = name,
    probes = probes.toList(),
    testName = testName
)

typealias ExecData = PersistentMap<Long, ExecDatum>

/**
 * A container for session runtime data and optionally runtime data of tests
 * TODO ad hoc implementation, rewrite to something more descent
 */
class ExecRuntime(
    realtimeHandler: RealtimeHandler?
) : (Long, String, Int, String) -> BooleanArray {

    private val realtime = realtimeHandler?.let(::Realtime)

    private val _execData = atomic(persistentHashMapOf<String, ExecData>())

    override fun invoke(
        id: Long,
        name: String,
        probeCount: Int,
        testName: String
    ): BooleanArray = _execData.updateAndGet { tests ->
        (tests[testName] ?: persistentHashMapOf()).let { execData ->
            if (id !in execData) {
                val mutatedData = execData.put(
                    id, ExecDatum(
                        id = id,
                        name = name,
                        probes = BooleanArray(probeCount),
                        testName = testName
                    )
                )
                tests.put(testName, mutatedData)
            } else tests
        }
    }.getValue(testName).getValue(id).also(::offer).probes

    fun collect(): Sequence<ExecDatum> = Sequence {
        _execData.value.values.iterator()
    }.flatMap { it.values.asSequence() }

    private fun offer(datum: ExecDatum) {
        realtime?.offer(datum)
    }

    fun close() {
        realtime?.close()
    }
}

/**
 * Simple probe array provider that employs a lock-free map for runtime data storage.
 * This class is intended to be an ancestor for a concrete probe array provider object.
 * The provider must be a Kotlin singleton object, otherwise the instrumented probe calls will fail.
 */
open class SimpleSessionProbeArrayProvider(
    private val instrContext: IDrillContex = DrillContext
) : SessionProbeArrayProvider {

    private val sessionRuntimes get() = _runtimes.value

    private val _runtimes = atomic(persistentHashMapOf<String, ExecRuntime>())

    private val _stubArray = atomic(BooleanArray(1024))

    override fun invoke(
        id: Long,
        name: String,
        probeCount: Int
    ): BooleanArray = instrContext()?.let { sessionId ->
        sessionRuntimes[sessionId]?.let { sessionRuntime ->
            val testName = instrContext[DRIlL_TEST_NAME] ?: "default"
            sessionRuntime(id, name, probeCount, testName)
        }
    } ?: stubArray(probeCount)

    override fun start(
        sessionId: String,
        realtimeHandler: RealtimeHandler?
    ) = _runtimes.update {
        if (sessionId !in it) {
            it.put(sessionId, ExecRuntime(realtimeHandler))
        } else it
    }

    override fun stop(sessionId: String): Sequence<ExecDatum>? = remove(sessionId)?.collect()

    override fun cancel(sessionId: String) = remove(sessionId).let { Unit }

    override fun cancelAll(): List<String> = _runtimes.getAndUpdate {
        it.clear()
    }.map { (id, runtime) ->
        runtime.close()
        id
    }

    private fun stubArray(probeCount: Int) = _stubArray.updateAndGet {
        if (probeCount > it.size) {
            var size = it.size shl 1
            while (size <= probeCount) {
                size = size shl 1
            }
            BooleanArray(size)
        } else it
    }

    private fun remove(sessionId: String): ExecRuntime? = _runtimes.getAndUpdate {
        it - sessionId
    }[sessionId]?.also(ExecRuntime::close)
}
