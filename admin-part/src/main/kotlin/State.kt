package com.epam.drill.plugins.test2code

import com.epam.drill.common.*
import com.epam.drill.plugin.api.*
import com.epam.drill.plugins.test2code.storage.*
import com.epam.kodux.*
import kotlinx.atomicfu.*
import org.jacoco.core.analysis.*
import org.jacoco.core.data.*

/**
 * Agent state.
 * The state itself holds only an atomic reference to the data.
 * The data is represented by the sealed class hierarchy AgentData.
 * In case of inconsistencies of the data a ClassCastException is thrown.
 */
class PluginInstanceState(
    val storeClient: StoreClient,
    val prevBuildVersion: String,
    val lastPrevBuildCoverage: Double,
    val agentInfo: AgentInfo,
    val buildManager: BuildManager
) {

    val data get() = _data.value

    val buildInfo: BuildInfo? get() = buildManager[agentInfo.buildVersion]

    val scopeManager = ScopeManager(storeClient)

    val activeScope get() = _activeScope.value

    val buildTests get() = _buildTests.value

    private val _data = atomic<AgentData>(NoData)

    private val _buildTests = atomic(BuildTests(agentInfo.id))

    private val _scopeCounter = atomic(0)

    private val _activeScope = atomic(ActiveScope(scopeName(), agentInfo.buildVersion))

    fun init() {
        _data.updateAndGet { ClassDataBuilder }
    }

    suspend fun initialized() {
        val classesBytes = buildInfo?.classesBytes ?: emptyMap()
        val coverageBuilder = CoverageBuilder()
        val analyzer = Analyzer(ExecutionDataStore(), coverageBuilder)
        classesBytes.forEach { analyzer.analyzeClass(it.value, it.key) }
        val bundleCoverage = coverageBuilder.getBundle("")

        val classesData = ClassesData(
            buildVersion = agentInfo.buildVersion,
            totalInstructions = bundleCoverage.instructionCounter.totalCount,
            prevBuildVersion = prevBuildVersion,
            prevBuildCoverage = lastPrevBuildCoverage
        )
        _data.value = classesData
        val buildTests = storeClient.findById(agentInfo.id) ?: storeClient.store(this.buildTests)
        _buildTests.value = buildTests
        storeClient.store(classesData)
    }

    suspend fun addBuildTests(buildVersion: String, tests: List<AssociatedTests>) {
        buildTests.add(buildVersion, tests)
        storeClient.store(buildTests)
    }

    suspend fun storeBuildCoverage(buildCoverage: BuildCoverage, risks: Risks, testsToRun: GroupedTests) {
        storeClient.store(
            LastBuildCoverage(
                id = lastCoverageId(agentInfo.id, agentInfo.buildVersion),
                coverage = buildCoverage.coverage,
                arrow = buildCoverage.arrow?.name,
                risks = risks.run { newMethods.count() + modifiedMethods.count() },
                testsToRun = TestsToRunDto(
                    groupedTests = testsToRun,
                    count = testsToRun.totalCount()
                )
            )
        )
    }

    suspend fun renameScope(id: String, newName: String) {
        val trimmedNewName = newName.trim()
        if (id == activeScope.id) activeScope.rename(trimmedNewName)
        else scopeManager.getScope(id)?.apply {
            scopeManager.saveScope(this.copy(name = newName, summary = this.summary.copy(name = trimmedNewName)))
        }
    }

    suspend fun toggleScope(id: String) {
        scopeManager.getScope(id)?.apply {
            scopeManager.saveScope(this.copy(enabled = !enabled, summary = this.summary.copy(enabled = !enabled)))
        }
    }


    suspend fun scopeNameNotExisting(name: String, buildVersion: String) =
        scopeManager.scopes(buildVersion)
            .find { it.name == name.trim() } == null && (name.trim() != activeScope.name || agentInfo.buildVersion != buildVersion)

    suspend fun scopeNotExisting(id: String) = scopeManager.getScope(id) == null && activeScope.id != id

    suspend fun classesData(buildVersion: String = agentInfo.buildVersion): AgentData = when (buildVersion) {
        agentInfo.buildVersion -> data as? ClassesData
        else -> storeClient.classesData(buildVersion)
    } ?: NoData


    fun changeActiveScope(name: String): ActiveScope = run {
        _activeScope.getAndUpdate { prevScope ->
            prevScope.close()
            ActiveScope(scopeName(name), agentInfo.buildVersion)
        }
    }

    private fun scopeName(name: String = "") = when (val trimmed = name.trim()) {
        "" -> "New Scope ${_scopeCounter.incrementAndGet()}"
        else -> trimmed
    }
}


private suspend fun StoreClient.classesData(buildVersion: String) = findBy<ClassesData> {
    ClassesData::buildVersion eq buildVersion
}.firstOrNull()

suspend fun StoreClient.readLastBuildCoverage(agentId: String, buildVersion: String): LastBuildCoverage? {
    return findById(lastCoverageId(agentId, buildVersion))
}

private fun lastCoverageId(agentId: String, buildVersion: String) = "$agentId:$buildVersion"
