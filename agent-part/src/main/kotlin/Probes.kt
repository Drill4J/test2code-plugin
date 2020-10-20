package com.epam.drill.plugins.test2code

import com.epam.drill.plugin.api.processing.*
import com.epam.drill.plugins.test2code.common.api.*
import kotlinx.atomicfu.*
import kotlinx.collections.immutable.*
import kotlinx.coroutines.*
import kotlin.coroutines.*

/**
 * Provides boolean array for the probe.
 * Implementations must be kotlin singleton objects.
 */
typealias ProbeArrayProvider = (Long, String, Int) -> BooleanArray

typealias RealtimeHandler = (Sequence<ExecDatum>) -> Unit

interface SessionProbeArrayProvider : ProbeArrayProvider {

    fun start(
        sessionId: String,
        isGlobal: Boolean,
        testName: String? = null,
        realtimeHandler: RealtimeHandler = {}
    ): List<String>

    fun stop(sessionId: String): Sequence<ExecDatum>?
    fun stopAll(): List<Pair<String, Sequence<ExecDatum>>>
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

internal object ProbeWorker : CoroutineScope {
    override val coroutineContext: CoroutineContext = run {
        java.util.concurrent.Executors.newFixedThreadPool(4).asCoroutineDispatcher() + SupervisorJob()
    }
}

/**
 * A container for session runtime data and optionally runtime data of tests
 * TODO ad hoc implementation, rewrite to something more descent
 */
class ExecRuntime(
    realtimeHandler: RealtimeHandler
) : (Long, String, Int, String) -> BooleanArray {

    private val _execData = atomic(persistentHashMapOf<String, ExecData>())

    private val job = ProbeWorker.launch {
        while (true) {
            delay(2000L)
            realtimeHandler(collect())
        }
    }

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
    }.getValue(testName).getValue(id).probes

    fun collect(): Sequence<ExecDatum> = _execData.getAndUpdate { it.clear() }.values.asSequence().run {
        flatMap { it.values.asSequence() }
    }

    fun close() {
        job.cancel()
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

    private val runtimes get() = _runtimes.value

    private val _context = atomic<IDrillContex?>(null)

    private val _runtimes = atomic(persistentHashMapOf<String, ExecRuntime>())

    private val _stubArray = atomic(BooleanArray(1024))

    override fun invoke(
        id: Long,
        name: String,
        probeCount: Int
    ): BooleanArray = _context.value?.let { context ->
        val sessionId = context()
        runtimes[sessionId]?.let { sessionRuntime ->
            val testName = context[DRIlL_TEST_NAME] ?: "default"
            sessionRuntime(id, name, probeCount, testName)
        }
    } ?: stubArray(probeCount)

    override fun start(
        sessionId: String,
        isGlobal: Boolean,
        testName: String?,
        realtimeHandler: RealtimeHandler
    ): List<String> = if (isGlobal) {
        val cancelled = cancelAll()
        _context.value = GlobalContext(sessionId, testName)
        add(sessionId, realtimeHandler)
        cancelled
    } else {
        val oldContext = _context.value
        val cancelled = if (oldContext != null && oldContext !== instrContext) {
            cancelAll()
        } else emptyList()
        _context.value = instrContext
        add(sessionId, realtimeHandler)
        cancelled
    }

    override fun stop(sessionId: String): Sequence<ExecDatum>? = remove(sessionId)?.collect()

    override fun stopAll(): List<Pair<String, Sequence<ExecDatum>>> = _runtimes.getAndUpdate {
        _context.value = null
        it.clear()
    }.map { (id, runtime) ->
        runtime.close()
        id to runtime.collect()
    }

    override fun cancel(sessionId: String) {
        remove(sessionId)
    }

    override fun cancelAll(): List<String> = _runtimes.getAndUpdate {
        _context.value = null
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

    private fun add(sessionId: String, realtimeHandler: RealtimeHandler) {
        _runtimes.update {
            if (sessionId !in it) {
                it.put(sessionId, ExecRuntime(realtimeHandler))
            } else it
        }
    }

    private fun remove(sessionId: String): ExecRuntime? = (_runtimes.getAndUpdate { runtimes ->
        (runtimes - sessionId).also {
            if (it.none()) {
                _context.value = null
            }
        }
    }[sessionId])?.also(ExecRuntime::close)

}

private class GlobalContext(
    private val sessionId: String,
    private val testName: String?
) : IDrillContex {
    override fun get(key: String): String? = testName?.takeIf { key == DRIlL_TEST_NAME }

    override fun invoke(): String? = sessionId
}
