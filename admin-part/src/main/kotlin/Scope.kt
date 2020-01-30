package com.epam.drill.plugins.test2code

import com.epam.kodux.*
import kotlinx.atomicfu.*
import kotlinx.collections.immutable.*
import kotlinx.serialization.*

interface Scope : Sequence<FinishedSession> {
    val id: String
    val buildVersion: String
    val summary: ScopeSummary
}

fun Sequence<Scope>.summaries(): List<ScopeSummary> = map(Scope::summary).toList()

class ActiveScope(name: String, override val buildVersion: String) : Scope {

    override val id = genUuid()

    private val _sessions = atomic(persistentListOf<FinishedSession>())

    private val started: Long = currentTimeMillis()

    private val _summary = atomic(
        ScopeSummary(
            id = id,
            name = name,
            started = started
        )
    )

    override val summary get() = _summary.value

    val name get() = summary.name

    val activeSessions = AtomicCache<String, ActiveSession>()

    fun update(session: FinishedSession, classesBytes: ClassesBytes?, totalInstructions: Int): ScopeSummary {
        _sessions.update { it.add(session) }
        return _summary.updateAndGet { summary ->
            summary.copy(
                coverage = classesBytes?.coverage(this, totalInstructions) ?: 0.0,
                coveragesByType = classesBytes?.coveragesByTestType(
                    classesBytes.bundlesByTests(this),
                    this,
                    totalInstructions
                ) ?: emptyMap()
            )
        }
    }

    fun rename(name: String): ScopeSummary = _summary.getAndUpdate { it.copy(name = name) }

    fun finish(enabled: Boolean) = FinishedScope(
        id = id,
        buildVersion = buildVersion,
        name = summary.name,
        enabled = enabled,
        summary = summary.copy(finished = currentTimeMillis(), active = false, enabled = enabled),
        probes = _sessions.value.asIterable().groupBy { it.testType }
    )

    override fun iterator(): Iterator<FinishedSession> = _sessions.value.iterator()

    fun startSession(msg: SessionStarted) {
        activeSessions(msg.sessionId) { ActiveSession(msg.sessionId, msg.testType) }
    }

    fun addProbes(msg: CoverDataPart) {
        activeSessions[msg.sessionId]?.let { activeSession ->
            for (probe in msg.data) {
                activeSession.append(probe)
            }
        }
    }

    fun cancelSession(msg: SessionCancelled) = activeSessions.remove(msg.sessionId)

    fun finishSession(msg: SessionFinished): FinishedSession? {
        return when (val activeSession = activeSessions.remove(msg.sessionId)) {
            null -> null
            else -> activeSession.finish()
        }
    }

    override fun toString() = "act-scope($id, $name)"
}

@Serializable
data class FinishedScope(
    @Id override val id: String,
    override val buildVersion: String,
    val name: String,
    override val summary: ScopeSummary,
    val probes: Map<String, List<FinishedSession>>,
    var enabled: Boolean = true
) : Scope {

    override fun iterator() = probes.values.flatten().iterator()

    override fun toString() = "fin-scope($id, $name)"
}
