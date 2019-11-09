package com.epam.drill.plugins.coverage

import com.epam.drill.plugin.api.processing.*
import java.util.concurrent.ConcurrentHashMap

/**
 * Provides boolean array for the probe.
 * Implementations must be kotlin singleton objects.
 */
typealias ProbeArrayProvider = (Long, String, Int) -> BooleanArray

interface SessionProbeArrayProvider : ProbeArrayProvider {
    fun start(sessionId: String, testType: String)
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


typealias ExecData = ConcurrentHashMap<Long, ExecDatum>

/**
 * A container for session runtime data and optionally runtime data of tests
 * TODO ad hoc implementation, rewrite to something more descent
 */
class ExecRuntime : (Long, String, Int, String) -> BooleanArray {

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
    }.probes

    fun collect() = execData.values.flatMap { it.values.toList() }
}

/**
 * Simple probe array provider that employs a lock-free map for runtime data storage.
 * This class is intended to be an ancestor for a concrete probe array provider object.
 * The provider must be a Kotlin singleton object, otherwise the instrumented probe calls will fail.
 */
open class SimpleSessionProbeArrayProvider(private val instrContext: IDrillContex = DrillContext) : SessionProbeArrayProvider {
    private val sessionRuntimes = ConcurrentHashMap<String, ExecRuntime>()

    override fun invoke(id: Long, name: String, probeCount: Int): BooleanArray {
        val sessionId = instrContext()
        return when(val sessionRuntime = if (sessionId != null) sessionRuntimes[sessionId] else null) {
            null -> BooleanArray(probeCount)
            else -> {
                val testName = instrContext[DRIlL_TEST_NAME] ?: "default"
                sessionRuntime(id, name, probeCount, testName)
            }
        }
    }

    override fun start(sessionId: String, testType: String) {
        sessionRuntimes[sessionId] = ExecRuntime()
    }

    override fun stop(sessionId: String) = sessionRuntimes.remove(sessionId)?.collect()?.asSequence()

    override fun cancel(sessionId: String) {
        sessionRuntimes.remove(sessionId)
    }

}