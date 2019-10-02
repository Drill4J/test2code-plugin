package com.epam.drill.plugins.coverage

import com.epam.drill.common.*
import kotlinx.atomicfu.*
import org.jacoco.core.analysis.*
import org.jacoco.core.data.*

/**
 * Agent state.
 * The state itself holds only an atomic reference to the data.
 * The data is represented by the sealed class hierarchy AgentData.
 * In case of inconsistencies of the data a ClassCastException is thrown.
 */

class AgentState(
    val agentInfo: AgentInfo,
    private val prevState: AgentState?
) {
    @Suppress("PropertyName")
    private val _data = atomic<AgentData>(NoData)

    var data: AgentData
        get() = _data.value
        private set(value) {
            _data.value = value
        }

    private val _lastBuildCoverage = atomic(prevState?.lastBuildCoverage ?: 0.0)

    internal var lastBuildCoverage: Double
        get() = _lastBuildCoverage.value
        set(value) {
            _lastBuildCoverage.value = value
        }

    val scopes = AtomicCache<String, FinishedScope>()

    val testsAssociatedWithBuild: TestsAssociatedWithBuild = prevState?.testsAssociatedWithBuild
        ?: testsAssociatedWithBuildStorageManager.getStorage(agentInfo.id, MutableMapTestsAssociatedWithBuild())

    private val _scopeCounter = atomic(0)

    private val _activeScope = atomic(ActiveScope(scopeName()))

    val activeScope get() = _activeScope.value

    val scopeSummaries get() = scopes.values.map { it.summary }

    fun init() {
        _data.updateAndGet { ClassDataBuilder() }
    }

    fun renameScope(id: String, newName: String) {
        val trimmedNewName = newName.trim()
        if (id == activeScope.id) activeScope.rename(trimmedNewName)
        else scopes[id]?.apply {
            scopes[id] = this.copy(name = newName, summary = this.summary.copy(name = trimmedNewName))
        }
    }

    fun scopeNameNotExisting(name: String) =
        scopes.values.find { it.name == name.trim() } == null && name.trim() != activeScope.name

    fun scopeNotExisting(id: String) = scopes[id] == null && activeScope.id != id

    fun refreshClasses(buildInfo: BuildInfo) {
        //throw ClassCastException if the ref value is in the wrong state
        val agentData = data as ClassDataBuilder
        buildInfo.classesBytes.forEach { (key, bytes) ->
            agentData.addClass(key, bytes)
        }
    }

    fun initialized(buildInfo: BuildInfo?) {
        //if buildInfo is null, agent has not been initialized properly
        refreshClasses(buildInfo!!)
        val diff = buildInfo.methodChanges
        //throw ClassCastException if the ref value is in the wrong state
        val agentData = data as ClassDataBuilder
        val coverageBuilder = CoverageBuilder()
        val analyzer = Analyzer(ExecutionDataStore(), coverageBuilder)
        agentData.classData.toMap()
        val classBytes = agentData.classData.asSequence()
            .onEach { analyzer.analyzeClass(it.second, it.first) }
            .toMap()
        val bundleCoverage = coverageBuilder.getBundle("")
        data = ClassesData(
            classesBytes = classBytes,
            totals = bundleCoverage.plainCopy,
            prevAgentInfo = prevState?.agentInfo,
            methodsChanges = diff,
            prevBuildCoverage = lastBuildCoverage,
            changed = diff.notEmpty
        )
    }

    fun reset() {
        data = NoData
        changeActiveScope("New Scope 1")
        scopes.clean()
    }

    //throw ClassCastException if the ref value is in the wrong state
    fun classesData(): ClassesData = data as ClassesData

    fun changeActiveScope(name: String) = _activeScope.getAndUpdate { ActiveScope(scopeName(name)) }

    private fun scopeName(name: String = "") = when (val trimmed = name.trim()) {
        "" -> "New Scope ${_scopeCounter.incrementAndGet()}"
        else -> trimmed
    }
}
