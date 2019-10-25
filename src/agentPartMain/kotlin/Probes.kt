package com.epam.drill.plugins.coverage

import java.util.concurrent.*

/**
 * Provides boolean array for the probe.
 * Implementations must be kotlin singleton objects.
 */
typealias ProbeArrayProvider = (Long, String, Int) -> BooleanArray

interface SessionProbeArrayProvider : ProbeArrayProvider {
    fun start(sessionId: String, testType: String, testName: String)
    fun stop(sessionId: String): Sequence<ExecDatum>?
    fun cancel(sessionId: String)
}

interface InstrContext : () -> String? {
    operator fun get(key: String): String?
}

class ExecDatum(
        val id: Long,
        val name: String,
        val probes: BooleanArray,
        val testName: String = ""
)


typealias ExecData = ConcurrentHashMap<Long, ExecDatum>

/**
 * A container for session runtime data and optionally runtime data of tests
 * TODO ad hoc implementation, rewrite to something more descent
 */
class ExecRuntime(val testName: String) : (Long, String, Int) -> BooleanArray {

    private val execData = ConcurrentHashMap<String, ExecData>()

    override fun invoke(
            id: Long,
            name: String,
            probeCount: Int
    ) = execData.getOrPut(testName) { ExecData() }.getOrPut(id) {
        ExecDatum(
                id = id,
                name = name,
                probes = BooleanArray(probeCount),
                testName = testName
        )
    }.probes

    fun collect() = execData.values.flatMap { it.values.toList() }
}

/**
 * Simple probe array provider that employs a lock-free map for runtime data storage.
 * This class is intended to be an ancestor for a concrete probe array provider object.
 * The provider must be a Kotlin singleton object, otherwise the instrumented probe calls will fail.
 */
open class SimpleSessionProbeArrayProvider(private val instrContext: InstrContext) : SessionProbeArrayProvider {
    private val sessionRuntimes = ConcurrentHashMap<String, ExecRuntime>()

    override fun invoke(id: Long, name: String, probeCount: Int): BooleanArray {
        val sessionId = instrContext() ?: DEFAULT_SESSION
        return when (val sessionRuntime = sessionRuntimes[sessionId]) {
            null -> BooleanArray(probeCount)
            else -> {
                sessionRuntime(id, name, probeCount)
            }
        }
    }

    override fun start(sessionId: String, testType: String, testName: String) {
        sessionRuntimes[sessionId] = ExecRuntime(testName)
    }

    override fun stop(sessionId: String) = sessionRuntimes.remove(sessionId)?.collect()?.asSequence()

    override fun cancel(sessionId: String) {
        sessionRuntimes.remove(sessionId)
    }

}
