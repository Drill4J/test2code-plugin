package com.epam.drill.plugins.test2code

import com.epam.drill.plugins.test2code.api.*
import com.epam.drill.plugins.test2code.common.api.*
import com.epam.drill.plugins.test2code.coverage.*
import com.epam.drill.plugins.test2code.util.*
import com.epam.kodux.*
import kotlinx.atomicfu.*
import kotlinx.collections.immutable.*
import kotlinx.coroutines.*
import kotlinx.serialization.*

interface Scope : Sequence<FinishedSession> {
    val id: String
    val buildVersion: String
    val name: String
    val summary: ScopeSummary
}

fun Sequence<Scope>.summaries(): List<ScopeSummary> = map(Scope::summary).toList()


typealias ActiveScopeHandler = suspend ActiveScope.(Boolean, Sequence<Session>?) -> Unit

class ActiveScope(
    override val id: String = genUuid(),
    override val buildVersion: String,
    val nth: Int = 1,
    name: String = "$DEFAULT_SCOPE_NAME $nth",
    sessions: List<FinishedSession> = emptyList()
) : Scope {

    private enum class Change(val sessions: Boolean, val probes: Boolean) {
        ONLY_SESSIONS(true, false),
        ONLY_PROBES(false, true),
        ALL(true, true)
    }

    override val summary get() = _summary.value

    override val name get() = summary.name

    val activeSessions = AtomicCache<String, ActiveSession>()

    private val _sessions = atomic(sessions.toPersistentList())

    //TODO remove summary for this class
    private val _summary = atomic(
        ScopeSummary(
            id = id,
            name = name,
            started = currentTimeMillis()
        )
    )

    private val  _handler = atomic<ActiveScopeHandler?>(null)

    private val _change = atomic<Change?>(null)

    private val changeJob = GlobalScope.launch {
        while (true) {
            _change.getAndUpdate { null }?.let { change ->
                _handler.value?.let { handler ->
                    val probes: Sequence<Session>? = if (change.probes) {
                        this@ActiveScope + activeSessions.values.asSequence()
                    } else null
                    handler(change.sessions, probes)
                }
            }
            delay(1000L)
        }
    }

    fun updateHandler(handler: ActiveScopeHandler) {
        _handler.value = handler
        _change.value = Change.ALL
    }

    //TODO remove summary related stuff from the active scope
    fun updateSummary(updater: (ScopeSummary) -> ScopeSummary) = _summary.updateAndGet(updater)

    fun rename(name: String): ScopeSummary = _summary.getAndUpdate { it.copy(name = name) }

    fun finish(enabled: Boolean) = FinishedScope(
        id = id,
        buildVersion = buildVersion,
        name = summary.name,
        enabled = enabled,
        summary = summary.copy(
            finished = currentTimeMillis(),
            active = false,
            enabled = enabled
        ),
        data = toList().let { sessions ->
            ScopeData(
                sessions = sessions,
                typedTests = sessions.flatMapTo(mutableSetOf(), Session::tests)
            )
        }
    )

    override fun iterator(): Iterator<FinishedSession> = _sessions.value.iterator()

    fun startSession(sessionId: String, testType: String, isRealtime: Boolean = false) {
        activeSessions(sessionId) { ActiveSession(sessionId, testType, isRealtime) }
        sessionsChanged()
    }

    fun addProbes(sessionId: String, probes: Collection<ExecClassData>) {
        activeSessions[sessionId]?.apply { addAll(probes) }
    }

    fun probesChanged() = _change.update {
        when(it) {
            Change.ONLY_SESSIONS, Change.ALL -> Change.ALL
            else -> Change.ONLY_PROBES
        }
    }

    fun cancelSession(
        msg: SessionCancelled
    ) = activeSessions.remove(msg.sessionId)?.also {
        if (it.any()) {
            _change.value = Change.ALL
        } else sessionsChanged()
    }

    fun cancelAllSessions() {
        activeSessions.clear().also { map ->
            if (map.values.any { it.any() } ) {
                _change.value = Change.ALL
            } else sessionsChanged()
        }
    }

    fun finishSession(
        sessionId: String
    ): FinishedSession? = activeSessions.remove(sessionId)?.run {
        finish().also { finished ->
            if (finished.probes.any()) {
                _sessions.update { it.add(finished) }
                _change.value = Change.ALL
            } else sessionsChanged()
        }
    }


    fun close() {
        _change.value = null
        activeSessions.clear()
        changeJob.cancel()
    }

    override fun toString() = "act-scope($id, $name)"

    private fun sessionsChanged() {
        _change.update {
            when (it) {
                Change.ONLY_PROBES, Change.ALL -> Change.ALL
                else -> Change.ONLY_SESSIONS
            }
        }
    }
}

@Serializable
data class ScopeData(
    val sessions: List<FinishedSession> = emptyList(),
    val typedTests: Set<TypedTest> = emptySet(),
    val bundleCounters: BundleCounters = BundleCounters.empty
) {
    companion object {
        val empty = ScopeData()
    }
}

@Serializable
data class FinishedScope(
    @Id override val id: String,
    override val buildVersion: String,
    override val name: String,
    override val summary: ScopeSummary,
    val enabled: Boolean,
    val data: ScopeData
) : Scope {
    override fun iterator() = data.sessions.iterator()

    override fun toString() = "fin-scope($id, $name)"
}

@Serializable
internal data class ActiveScopeInfo(
    @Id val buildVersion: String,
    val id: String = genUuid(),
    val nth: Int = 1,
    val name: String = "",
    val startedAt: Long = 0L
)

internal fun ActiveScopeInfo.inc() = copy(nth = nth.inc())
