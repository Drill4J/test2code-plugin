package com.epam.drill.plugins.test2code

import com.epam.drill.plugins.test2code.api.*
import com.epam.drill.plugins.test2code.common.api.*
import com.epam.drill.plugins.test2code.coverage.*
import com.epam.drill.plugins.test2code.util.*
import com.epam.kodux.*
import kotlinx.atomicfu.*
import kotlinx.collections.immutable.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*
import kotlinx.serialization.*

interface Scope : Sequence<FinishedSession> {
    val id: String
    val buildVersion: String
    val name: String
    val summary: ScopeSummary
}

fun Sequence<Scope>.summaries(): List<ScopeSummary> = map(Scope::summary).toList()

class ActiveScope(
    override val id: String = genUuid(),
    override val buildVersion: String,
    val nth: Int = 1,
    name: String = "$DEFAULT_SCOPE_NAME $nth",
    sessions: List<FinishedSession> = emptyList()
) : Scope {

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

    private val changes get() = _changes.value
    private val _changes = atomic<Channel<Unit>?>(null)

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
    }

    fun addProbes(sessionId: String, probes: Collection<ExecClassData>) {
        activeSessions[sessionId]?.apply { addAll(probes) }
    }

    fun sessionChanged() = changes?.takeIf { !it.isClosedForSend }?.run {
        offer(Unit)
    }

    fun cancelSession(msg: SessionCancelled) = activeSessions.remove(msg.sessionId)?.also {
        sessionChanged()
    }

    fun cancelAllSessions() = activeSessions.clear().also {
        if (it.any()) {
            sessionChanged()
        }
    }

    fun finishSession(
        sessionId: String,
        onSuccess: ActiveScope.(FinishedSession) -> Unit
    ): FinishedSession? = activeSessions.remove(sessionId)?.run {
        finish().also { finished ->
            if (finished.probes.any()) {
                _sessions.update { it.add(finished) }
                onSuccess(finished)
            }
        }
    }

    fun subscribeOnChanges(
        clb: suspend ActiveScope.(Sequence<Session>) -> Unit
    ) = _changes.update {
        it ?: Channel<Unit>().also {
            GlobalScope.launch {
                it.consumeEach {
                    val actSessionSeq = activeSessions.values.asSequence()
                    clb(this@ActiveScope + actSessionSeq)
                }
            }
        }
    }

    fun close() = changes?.close()

    override fun toString() = "act-scope($id, $name)"
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
data class AgentBuildId(
    val agentId: String,
    val buildVersion: String
)

@Serializable
internal data class ActiveScopeInfo(
    @Id val buildVersion: String,
    val id: String = genUuid(),
    val nth: Int = 1,
    val name: String = "",
    val startedAt: Long = 0L
)

internal fun ActiveScopeInfo.inc() = copy(nth = nth.inc())
