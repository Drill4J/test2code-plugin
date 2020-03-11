package com.epam.drill.plugins.test2code

import com.epam.drill.plugin.api.processing.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.util.concurrent.*
import kotlin.coroutines.*

/**
 * Provides boolean array for the probe.
 * Implementations must be kotlin singleton objects.
 */
typealias ProbeArrayProvider = (Long, String, Int) -> BooleanArray

interface SessionProbeArrayProvider : ProbeArrayProvider {
    fun start(sessionId: String, testType: String, eventCallback: (Sequence<ExecDatum>) -> Unit = {})
    fun stop(sessionId: String): Sequence<ExecDatum>?
    fun cancel(sessionId: String)
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

typealias ExecData = ConcurrentHashMap<Long, ExecDatum>

object ProbesWorker : CoroutineScope {
    override val coroutineContext: CoroutineContext =
        Executors.newFixedThreadPool(4).asCoroutineDispatcher() + SupervisorJob()

    operator fun invoke(block: suspend () -> Unit) = launch { block() }

}

/**
 * A container for session runtime data and optionally runtime data of tests
 * TODO ad hoc implementation, rewrite to something more descent
 */
class ExecRuntime(
    private val probesDataStream: TimeSpanEventBus<ExecDatum> = TimeSpanEventBusImpl(
        10,
        coroutineScope = GlobalScope
    )
) : (Long, String, Int, String) -> BooleanArray, TimeSpanEventBus<ExecDatum> by probesDataStream {

    private val execData = ConcurrentHashMap<String, ExecData>()

    override fun invoke(
        id: Long,
        name: String,
        probeCount: Int,
        testName: String
    ) = execData.getOrPut(testName) { ExecData() }.getOrPut(id) {
        ExecDatum(
            id = id,
            name = name,
            probes = BooleanArray(probeCount),
            testName = testName
        )
    }.also {
        ProbesWorker {
            probesDataStream.send(it)
        }
    }.probes

    fun collect() = execData.values.asSequence().flatMap { it.values.asSequence() }
}

/**
 * Simple probe array provider that employs a lock-free map for runtime data storage.
 * This class is intended to be an ancestor for a concrete probe array provider object.
 * The provider must be a Kotlin singleton object, otherwise the instrumented probe calls will fail.
 */
open class SimpleSessionProbeArrayProvider(private val instrContext: IDrillContex = DrillContext) :
    SessionProbeArrayProvider {
    private val sessionRuntimes = ConcurrentHashMap<String, ExecRuntime>()

    override fun invoke(id: Long, name: String, probeCount: Int): BooleanArray {
        val sessionId = instrContext()
        return when (val sessionRuntime = if (sessionId != null) sessionRuntimes[sessionId] else null) {
            null -> BooleanArray(probeCount)
            else -> {
                val testName = instrContext[DRIlL_TEST_NAME] ?: "default"
                sessionRuntime(id, name, probeCount, testName)
            }
        }
    }

    override fun start(
        sessionId: String, testType: String, eventCallback: (Sequence<ExecDatum>) -> Unit
    ) {
        val execRuntime = ExecRuntime()
        sessionRuntimes[sessionId] = execRuntime
        ProbesWorker.launch {
            execRuntime.collect {
                eventCallback(it)
            }
        }

    }

    override fun stop(sessionId: String): Sequence<ExecDatum>? {
        return sessionRuntimes.remove(sessionId)?.collect()
    }

    override fun cancel(sessionId: String) {
        sessionRuntimes.remove(sessionId)?.close()
    }

}
