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

import com.epam.drill.plugins.test2code.api.Label
import com.epam.drill.plugins.test2code.api.ScopeSummary
import com.epam.drill.plugins.test2code.api.routes.Routes
import com.epam.drill.plugins.test2code.common.api.ExecClassData
import com.epam.drill.plugins.test2code.coverage.BundleCounter
import com.epam.drill.plugins.test2code.coverage.TestKey
import com.epam.drill.plugins.test2code.coverage.testKey
import com.epam.drill.plugins.test2code.storage.AgentKey
import com.epam.drill.plugins.test2code.util.AtomicCache
import com.epam.drill.plugins.test2code.util.currentTimeMillis
import com.epam.drill.plugins.test2code.util.genUuid
import com.epam.drill.plugins.test2code.util.values
import com.epam.dsm.Id
import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.getAndUpdate
import kotlinx.atomicfu.update
import kotlinx.atomicfu.updateAndGet
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.minus
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import java.lang.ref.SoftReference


fun Sequence<IScope>.summaries(): List<ScopeSummary> = map(IScope::summary).toList()

typealias SoftBundleByTests = SoftReference<PersistentMap<TestKey, BundleCounter>>

typealias CoverageHandler = suspend Scope.(Boolean, Sequence<Session>?) -> Unit

typealias BundleCacheHandler = suspend Scope.(Map<TestKey, Sequence<ExecClassData>>) -> Unit

private val logger = logger {}

interface IScope : Sequence<FinishedSession> {
    val id: String
    val agentKey: AgentKey
    val name: String
    val summary: ScopeSummary
}

// store const ID for each scopes
private val persistentID: String = genUuid()

class Scope(
    override val id: String = persistentID,
    override val agentKey: AgentKey,
    val sessions: List<FinishedSession> = emptyList(),
    val data: ScopeData = ScopeData.empty
) : IScope {

    private val _bundleByTests = atomic<SoftBundleByTests>(SoftReference(persistentMapOf()))

    val activeSessions = AtomicCache<String, ActiveSession>()

    private val _sessions = atomic(sessions.toMutableList())

    private val _realtimeCoverageHandler = atomic<CoverageHandler?>(null)

    private val _bundleCacheHandler = atomic<BundleCacheHandler?>(null)

    private val _change = atomic<Change?>(null)

    override val name = "Single Scope"
    override val summary get() = _summary.value

    private val _summary = atomic(
        ScopeSummary(
            id = id,
            name = name,
            started = currentTimeMillis(),
            sessionsFinished = sessions.size,
        )
    )

    fun updateSummary(updater: (ScopeSummary) -> ScopeSummary) = _summary.updateAndGet(updater)

    fun rename(name: String): ScopeSummary = _summary.updateAndGet { it.copy(name = name) }

    val bundleByTests: PersistentMap<TestKey, BundleCounter>
        get() = _bundleByTests.value.get() ?: persistentMapOf()

    private enum class Change(val sessions: Boolean, val probes: Boolean) {
        ONLY_SESSIONS(true, false),
        ONLY_PROBES(false, true),
        ALL(true, true)
    }

    private val realtimeCoverageJob = AsyncJobDispatcher.launch {
        while (true) {
            delay(250)
            _change.value?.let {
                delay(250)
                _change.getAndUpdate { null }?.let { change ->
                    _realtimeCoverageHandler.value?.let { handler ->
                        val probes = if (change.probes) {
                            this@Scope + activeSessions.values.filter { it.isRealtime }
                        } else null
                        handler(change.sessions, probes)
                        delay(500)
                    }
                }
            }
        }
    }

    private val bundleCacheJob = AsyncJobDispatcher.launch {
        while (true) {
            val tests = activeSessions.values.flatMap { it.updatedTests }
            _bundleCacheHandler.value.takeIf { tests.any() }?.let {
                val probes = this@Scope + activeSessions.values
                val probesByTests = probes.groupBy { it.testType }.map { (testType, sessions) ->
                    sessions.asSequence().flatten()
                        .groupBy { it.testId.testKey(testType) }
                        .filter { it.key in tests }
                        .mapValuesTo(mutableMapOf()) { it.value.asSequence() }
                }.takeIf { it.isNotEmpty() }?.reduce { m1, m2 ->
                    m1.apply { putAll(m2) }
                } ?: emptyMap()
                it(probesByTests)
            }
            delay(2000)
        }
    }

    fun initRealtimeHandler(handler: CoverageHandler): Boolean = _realtimeCoverageHandler.getAndUpdate {
        it ?: handler.also { _change.value = Change.ALL }
    } == null

    fun initBundleHandler(handler: BundleCacheHandler): Boolean = _bundleCacheHandler.getAndUpdate {
        it ?: handler
    } == null

    override fun iterator(): Iterator<FinishedSession> = _sessions.value.iterator()
    fun sessions(): MutableList<FinishedSession> = _sessions.value

    fun startSession(
        sessionId: String,
        testType: String,
        isGlobal: Boolean = false,
        envId: String = "",
        isRealtime: Boolean = false,
        testName: String? = null,
        labels: MutableSet<Label> = mutableSetOf<Label>(),
    ): ActiveSession {
        val newSession = ActiveSession(sessionId, testType, isGlobal, envId, isRealtime, testName, labels)
        activeSessions(newSession.id) { existingSession ->
            if (existingSession != null) {
                throw FieldError(
                    name = "sessionId",
                    message = "Session with such id ${newSession.id} is already started. Please provide id unique across all envs"
                )
            }

            if (newSession.isGlobal) {
                val envGlobalSession = activeSessions.values.find { it.envId == newSession.envId && it.isGlobal }
                if (envGlobalSession != null) {
                    throw FieldError(
                        name = "isGlobal",
                        message = "Global sessions for env ${newSession.envId} is already started."
                    )
                }
            }
            newSession
        }
        sessionsChanged()
        return newSession
    }

    fun activeSessionOrNull(id: String): ActiveSession? = activeSessions.values.find { it.id == id }

    fun addProbes(
        sessionId: String,
        probeProvider: () -> Collection<ExecClassData>,
    ): ActiveSession? {
        val session = activeSessions.values.find { it.id == sessionId }
        session?.addAll(probeProvider())
        return session
    }

    fun addBundleCache(bundleByTests: Map<TestKey, BundleCounter>) {
        _bundleByTests.update {
            val bundles = (it.get() ?: persistentMapOf()).putAll(bundleByTests)
            SoftReference(bundles)
        }
    }

    fun probesChanged() = _change.update {
        when (it) {
            Change.ONLY_SESSIONS, Change.ALL -> Change.ALL
            else -> Change.ONLY_PROBES
        }
    }

    fun cancelSession(
        sessionId: String,
    ): ActiveSession? = removeSession(sessionId)?.also {
        clearBundleCache()
        if (it.any()) {
            _change.value = Change.ALL
        } else sessionsChanged()
    }

    fun cancelAllSessions() = activeSessions.clear().also { map ->
        clearBundleCache()
        if (map.values.any { it.any() }) {
            _change.value = Change.ALL
        } else sessionsChanged()
    }

    fun finish(enabled: Boolean) = FinishedScope(
        id = id,
        agentKey = agentKey,
        name = summary.name,
        enabled = enabled,
        summary = summary.copy(
            finished = currentTimeMillis(),
            active = false,
            enabled = enabled
        ),
        data = ScopeData(
            sessions = toList(),
        )
    )

    fun finishSession(
        sessionId: String,
    ): FinishedSession? = removeSession(sessionId)?.run {
        finish().also { finished ->
            if (finished.probes.any()) {
                val updatedSessions = _sessions.updateAndGet { it.apply { add(finished) } }
                _bundleByTests.update {
                    val current = it.get() ?: persistentMapOf()
                    SoftReference(current - updatedTests)
                }
                _summary.update { it.copy(sessionsFinished = updatedSessions.count()) }
                _change.value = Change.ALL
            } else sessionsChanged()
        }
    }


    fun close() {
        logger.debug { "closing active scope $id..." }
        _change.value = null
        activeSessions.clear()
        realtimeCoverageJob.cancel()
        bundleCacheJob.cancel()
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

    private fun clearBundleCache() = _bundleByTests.update { SoftReference(persistentMapOf()) }

    private fun removeSession(id: String): ActiveSession? = activeSessions.run {
        remove(id)
    }
}

@Serializable
data class FinishedScope(
    @Id override val id: String,
    override val agentKey: AgentKey,
    override val name: String,
    override val summary: ScopeSummary,
    val enabled: Boolean,
    val data: ScopeData,
) : IScope {
    override fun iterator() = data.sessions.iterator()

    override fun toString() = "fin-scope($id, $name)"
}

@Serializable
internal data class ScopeInfo(
    @Id val agentKey: AgentKey,
    val id: String = genUuid(),
    val nth: Int = 1,
    val name: String = "",
    val startedAt: Long = 0L,
)

@Serializable
data class ScopeData(
    @Transient
    val sessions: List<FinishedSession> = emptyList(),
) {
    companion object {
        val empty = ScopeData()
    }
}

internal fun ScopeInfo.inc() = copy(nth = nth.inc())

fun scopeById(scopeId: String) = Routes.Build.Scopes(Routes.Build()).let {
    Routes.Build.Scopes.Scope(scopeId, it)
}
