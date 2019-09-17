package com.epam.drill.plugins.coverage

import com.epam.drill.common.*
import com.epam.drill.plugins.coverage.storage.*
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
    prevState: AgentState?
) {
    @Suppress("PropertyName")
    private val _data = atomic(prevState?.data ?: NoData)
    private val _backup = atomic<AgentData>(NoData)

    internal var data: AgentData
        get() = _data.value
        private set(value) {
            _data.value = value
        }

    private var backup: AgentData
        get() = _backup.value
        private set(value) {
            _backup.value = value
        }

    val scopeManager: ScopeManager = XodusScopeManager(agentInfo.id)

    private val _scopeCounter = atomic(0)

    private val _activeScope = atomic(ActiveScope(scopeName()))

    val activeScope get() = _activeScope.value

    fun init(initInfo: InitInfo) {
        _data.updateAndGet { prevData ->
            ClassDataBuilder(
                count = initInfo.classesCount,
                prevData = prevData as? ClassesData
            )
        }
    }

    fun scopeSummariesByBuild(buildVersion: String) = scopeManager.allScopesByBuild(buildVersion).summaries()

    fun renameScope(id: String, newName: String) {
        val trimmedNewName = newName.trim()
        if (id == activeScope.id) activeScope.rename(trimmedNewName)
        else scopeManager[id]?.apply {
            scopeManager.save(this.copy(name = newName, summary = this.summary.copy(name = trimmedNewName)))
        }
    }

    fun toggleScope(id: String): FinishedScope? =
        scopeManager[id]?.run {
            scopeManager.save(this.copy(enabled = !enabled, summary = this.summary.copy(enabled = !enabled)))
        }

    fun scopeNameNotExisting(name: String, buildVersion: String = agentInfo.buildVersion) =
        scopeManager.allScopesByBuild(buildVersion)
            .find { it.name == name.trim() } == null && name.trim() != activeScope.name

    fun scopeBuildVersion(id: String) = scopeManager[id]?.buildVersion ?: if (activeScope.id == id) {
        agentInfo.buildVersion
    } else null

    fun scopeSummary(id: String) = scopeManager[id]?.summary ?: if (activeScope.id == id) {
        activeScope.summary
    } else null

    fun addClass(key: String, bytes: ByteArray) {
        //throw ClassCastException if the ref value is in the wrong state
        val agentData = data as ClassDataBuilder
        agentData.addClass(key, bytes)
    }

    fun initialized() {
        //throw ClassCastException if the ref value is in the wrong state
        val agentData = data as ClassDataBuilder
        val coverageBuilder = CoverageBuilder()
        val analyzer = Analyzer(ExecutionDataStore(), coverageBuilder)
        agentData.classData.toMap()
        val classBytes = agentData.classData.asSequence()
            .onEach { analyzer.analyzeClass(it.second, it.first) }
            .toMap()
        val bundleCoverage = coverageBuilder.getBundle("")
        val currentMethods = classBytes.mapValues { (className, bytes) ->
            BcelClassParser(bytes, className).parseToJavaMethods()
        }
        val analysisResult = analyzeMethods(agentData, bundleCoverage, currentMethods)
        data = ClassesData(
            agentInfo = agentInfo,
            classesBytes = classBytes,
            totals = bundleCoverage.plainCopy,
            javaMethods = currentMethods,
            prevAgentInfo = analysisResult.prevData?.agentInfo,
            methodsChanges = analysisResult.diff,
            prevBuildCoverage = analysisResult.prevData?.lastBuildCoverage ?: 0.0,
            changed = analysisResult.changed
        )

        backup = analysisResult.prevData ?: NoData
    }

    private fun analyzeMethods(
        agentData: ClassDataBuilder,
        bundleCoverage: IBundleCoverage?,
        currentMethods: Map<String, List<JavaMethod>>
    ): MethodsAnalysisResult {
        val result = calculateDiff(agentData.prevData, bundleCoverage, currentMethods)
        return if (!result.changed) {
            calculateDiff(backup as? ClassesData, bundleCoverage, currentMethods)
        } else result
    }

    private fun calculateDiff(
        prevData: ClassesData?,
        bundleCoverage: IBundleCoverage?,
        currentMethods: Map<String, List<JavaMethod>>
    ): MethodsAnalysisResult {
        val prevMethods = prevData?.javaMethods ?: mapOf()
        val diff = MethodsComparator(bundleCoverage).compareClasses(prevMethods, currentMethods)
        val prevAgentInfo = prevData?.agentInfo
        val changed = prevAgentInfo != agentInfo || diff.notEmpty()
        return MethodsAnalysisResult(diff, changed, prevData)
    }

    fun reset() {
        data = NoData
        changeActiveScope("New Scope 1")
        scopeManager.clean()
    }

    //throw ClassCastException if the ref value is in the wrong state
    fun classesData(): ClassesData = data as ClassesData

    fun changeActiveScope(name: String) = _activeScope.getAndUpdate { ActiveScope(scopeName(name)) }

    private fun scopeName(name: String = "") = when (val trimmed = name.trim()) {
        "" -> "New Scope ${_scopeCounter.incrementAndGet()}"
        else -> trimmed
    }
}

class MethodsAnalysisResult(
    val diff: MethodChanges,
    val changed: Boolean,
    val prevData: ClassesData?
)